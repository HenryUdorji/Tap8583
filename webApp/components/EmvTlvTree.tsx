"use client";

import React, { useState } from "react";
import { ChevronDown, ChevronRight, Cpu, Eye, Info, ShieldAlert, ShieldCheck } from "lucide-react";
import { TlvEntryDto } from "@/lib/types";

interface EmvTlvTreeProps {
  entries: TlvEntryDto[];
}

export const EmvTlvTree: React.FC<EmvTlvTreeProps> = ({ entries }) => {
  if (!entries || entries.length === 0) {
    return (
      <div className="p-4 text-xs text-slate-500 font-mono italic">
        No EMV / TLV tags present in this packet.
      </div>
    );
  }

  return (
    <div className="space-y-1 font-mono text-xs select-text">
      {entries.map((entry, idx) => (
        <TlvNode key={`${entry.tag}-${idx}`} entry={entry} depth={0} />
      ))}
    </div>
  );
};

const TlvNode: React.FC<{ entry: TlvEntryDto; depth: number }> = ({ entry, depth }) => {
  const [expanded, setExpanded] = useState(true);
  const hasChildren = entry.children && entry.children.length > 0;

  // TVR Tag 95 bit breakdown
  const isTvr = entry.tag.toUpperCase() === "95" && entry.valueHex.length >= 10;
  const tvrChecks = isTvr ? decodeTvr(entry.valueHex) : [];

  return (
    <div className="border border-white/5 bg-slate-900/40 rounded-lg overflow-hidden my-1">
      {/* Node Header */}
      <div
        onClick={() => hasChildren && setExpanded(!expanded)}
        className={`flex items-center justify-between p-2 hover:bg-slate-800/50 transition-colors ${
          hasChildren ? "cursor-pointer" : ""
        }`}
        style={{ paddingLeft: `${Math.max(8, depth * 16 + 8)}px` }}
      >
        <div className="flex items-center gap-2">
          {hasChildren ? (
            expanded ? (
              <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
            ) : (
              <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
            )
          ) : (
            <div className="w-3.5 h-3.5 flex items-center justify-center">
              <span className="w-1.5 h-1.5 rounded-full bg-orange-400/80" />
            </div>
          )}

          {/* Tag Chip */}
          <span className="px-1.5 py-0.5 rounded bg-orange-500/10 border border-orange-500/30 text-orange-400 font-bold text-[11px]">
            {entry.tag}
          </span>

          {/* Tag Name */}
          <span className="text-slate-200 font-medium text-xs">
            {entry.name || `Tag 0x${entry.tag}`}
          </span>
        </div>

        {/* Byte Length & Hex */}
        <div className="flex items-center gap-2 text-[11px] text-slate-400">
          <span className="px-1 rounded bg-black/40 text-slate-400">
            {entry.length} {entry.length === 1 ? "byte" : "bytes"}
          </span>
        </div>
      </div>

      {/* Node Value / Decoded text */}
      <div className="px-3 pb-2 pt-0.5" style={{ paddingLeft: `${Math.max(28, depth * 16 + 28)}px` }}>
        {entry.decoded && (
          <div className="text-sky-300 font-semibold text-[11px] mb-1 flex items-center gap-1.5">
            <Info className="w-3 h-3 text-sky-400 flex-shrink-0" />
            <span>{entry.decoded}</span>
          </div>
        )}

        <div className="bg-black/30 border border-white/5 rounded px-2 py-1 text-[10px] text-slate-300 font-mono break-all">
          <span className="text-slate-500 mr-2">HEX:</span>
          {formatHex(entry.valueHex)}
        </div>

        {/* Special TVR Tag 95 Bit breakdown */}
        {isTvr && tvrChecks.length > 0 && (
          <div className="mt-2 space-y-1 border-t border-white/5 pt-1.5">
            <div className="text-[10px] text-slate-400 font-bold uppercase tracking-wider mb-1">
              Terminal Verification Results (TVR) Breakdown:
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-1 text-[10px]">
              {tvrChecks.map((check, i) => (
                <div
                  key={i}
                  className={`flex items-center gap-1.5 px-2 py-1 rounded border ${
                    check.failed
                      ? "bg-amber-500/10 border-amber-500/30 text-amber-300"
                      : "bg-emerald-500/10 border-emerald-500/20 text-emerald-400"
                  }`}
                >
                  {check.failed ? (
                    <ShieldAlert className="w-3 h-3 text-amber-400 flex-shrink-0" />
                  ) : (
                    <ShieldCheck className="w-3 h-3 text-emerald-400 flex-shrink-0" />
                  )}
                  <span className="truncate">{check.label}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Children if constructed */}
      {hasChildren && expanded && (
        <div className="border-t border-white/5 bg-black/20 p-1">
          {entry.children!.map((child, cIdx) => (
            <TlvNode key={`${child.tag}-${cIdx}`} entry={child} depth={depth + 1} />
          ))}
        </div>
      )}
    </div>
  );
};

function formatHex(hex: string): string {
  return hex.match(/.{1,2}/g)?.join(" ") || hex;
}

function decodeTvr(hex: string): { label: string; failed: boolean }[] {
  // 5 bytes (10 hex chars)
  const bytes = [];
  for (let i = 0; i < 10; i += 2) {
    bytes.push(parseInt(hex.slice(i, i + 2), 16) || 0);
  }

  const b0 = bytes[0] || 0;
  const b1 = bytes[1] || 0;
  const b2 = bytes[2] || 0;
  const b3 = bytes[3] || 0;
  const b4 = bytes[4] || 0;

  return [
    { label: "Offline data auth performed", failed: (b0 & 0x80) !== 0 },
    { label: "SDA failed", failed: (b0 & 0x40) !== 0 },
    { label: "ICC data missing", failed: (b0 & 0x20) !== 0 },
    { label: "Card on exception file", failed: (b0 & 0x10) !== 0 },
    { label: "DDA failed", failed: (b0 & 0x08) !== 0 },
    { label: "CDA failed", failed: (b0 & 0x04) !== 0 },
    { label: "Cardholder verification failed", failed: (b1 & 0x80) !== 0 },
    { label: "PIN try limit exceeded", failed: (b1 & 0x20) !== 0 },
    { label: "PIN pad defective", failed: (b1 & 0x10) !== 0 },
    { label: "Transaction exceeds floor limit", failed: (b3 & 0x80) !== 0 },
    { label: "Lower consecutive limit exceeded", failed: (b3 & 0x40) !== 0 },
    { label: "Upper consecutive limit exceeded", failed: (b3 & 0x20) !== 0 },
    { label: "Transaction selected randomly", failed: (b3 & 0x10) !== 0 },
    { label: "Merchant forced transaction online", failed: (b3 & 0x08) !== 0 },
    { label: "Script processing failed before auth", failed: (b4 & 0x20) !== 0 },
    { label: "Script processing failed after auth", failed: (b4 & 0x10) !== 0 },
  ];
}

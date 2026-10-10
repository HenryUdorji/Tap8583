"use client";

import React, { useState } from "react";
import { Check, Copy, FileText, Info, Layers, Tag, X } from "lucide-react";
import { DecodedFieldDto } from "@/lib/types";
import {
  CURRENCY_CODES,
  getCategoryBadgeClass,
  MERCHANT_CATEGORY_CODES,
  POS_ENTRY_MODES,
  RESPONSE_CODES,
} from "@/lib/referenceTables";

interface FieldDetailPanelProps {
  field: DecodedFieldDto | null;
  /** When set (drawer mode), a close button is shown in the header. */
  onClose?: () => void;
}

export const FieldDetailPanel: React.FC<FieldDetailPanelProps> = ({ field, onClose }) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (!field) {
    return (
      <div className="w-full h-full flex flex-col items-center justify-center bg-panel border-l border-white/10 p-6 text-center text-slate-500 select-none">
        <Info className="w-8 h-8 text-slate-700 mb-2" />
        <div className="text-xs font-semibold text-slate-400">Field Deep Dive</div>
        <div className="text-[11px] text-slate-500 mt-1">
          Click any ISO 8583 data element in the center inspector to see full technical specifications, subfields, and contextual reference tables.
        </div>
      </div>
    );
  }

  const badge = getCategoryBadgeClass(field.category);

  // Convert raw value to Hex bytes
  const rawHex = field.isBinary
    ? field.rawValue.match(/.{1,2}/g)?.join(" ") || field.rawValue
    : Array.from(field.rawValue)
        .map((c) => c.charCodeAt(0).toString(16).padStart(2, "0").toUpperCase())
        .join(" ");

  return (
    <div className="w-full flex flex-col h-full bg-panel border-l border-white/10 overflow-y-auto select-text font-mono text-xs">
      {/* Panel Header */}
      <div className="px-4 py-3 bg-slate-900/90 border-b border-white/10 flex items-center justify-between sticky top-0 z-10">
        <div className="flex items-center gap-2">
          <span className="px-1.5 py-0.5 rounded bg-white/10 text-white font-bold text-xs">
            DE {field.number}
          </span>
          <span className="text-slate-200 font-semibold truncate max-w-[180px]">
            {field.shortName || field.name}
          </span>
        </div>
        <button
          onClick={() => handleCopy(field.rawValue)}
          title="Copy value"
          className="p-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors"
        >
          {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
        </button>
        {onClose && (
          <button
            onClick={onClose}
            aria-label="Close field details"
            className="ml-2 p-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        )}
      </div>

      <div className="p-4 space-y-4">
        {/* Full Name & Category */}
        <div className="bg-slate-900/60 border border-white/10 rounded-xl p-3 space-y-2">
          <div className="text-[11px] text-slate-400 font-sans font-medium uppercase tracking-wider">
            Field Name
          </div>
          <div className="text-slate-100 font-sans font-bold text-sm">
            {field.name}
          </div>
          <div className="flex items-center gap-2 pt-1">
            <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${badge.bg} ${badge.text} ${badge.border}`}>
              {field.category.replace("_", " ")}
            </span>
            <span className="text-[10px] text-slate-400">
              {field.isoType} · {field.dataType}
            </span>
          </div>
        </div>

        {/* Primary Decoded Value */}
        {field.decodedValue && (
          <div className="bg-sky-500/10 border border-sky-500/30 rounded-xl p-3">
            <div className="text-[10px] text-sky-400 font-sans font-bold uppercase tracking-wider mb-1">
              Decoded Interpretation
            </div>
            <div className="text-sky-200 font-semibold text-xs leading-relaxed">
              {field.decodedValue}
            </div>
          </div>
        )}

        {/* Raw Value */}
        <div className="bg-slate-900/60 border border-white/10 rounded-xl p-3 space-y-1.5">
          <div className="flex items-center justify-between text-[10px] text-slate-400 uppercase tracking-wider">
            <span>Raw Value</span>
            <span>Length: {field.rawValue.length}</span>
          </div>
          <div className="text-slate-200 font-mono text-xs break-all bg-black/40 p-2 rounded border border-white/5">
            {field.rawValue}
          </div>
        </div>

        {/* Raw Hex Bytes */}
        <div className="bg-slate-900/60 border border-white/10 rounded-xl p-3 space-y-1.5">
          <div className="flex items-center justify-between text-[10px] text-slate-400 uppercase tracking-wider">
            <span>Raw Hex Bytes</span>
            <button
              onClick={() => handleCopy(rawHex)}
              className="text-sky-400 hover:text-sky-300 text-[10px] flex items-center gap-1"
            >
              <Copy className="w-3 h-3" />
              <span>Copy Hex</span>
            </button>
          </div>
          <div className="text-slate-300 font-mono text-[11px] break-all bg-black/40 p-2 rounded border border-white/5">
            {rawHex}
          </div>
        </div>

        {/* Contextual Reference Tables */}
        {/* 1. DE39: Response Codes */}
        {field.number === 39 && (
          <div className="bg-slate-900/60 border border-white/10 rounded-xl p-3 space-y-2">
            <div className="text-[10px] text-slate-400 uppercase tracking-wider font-bold">
              Response Code Reference Table
            </div>
            <div className="max-h-60 overflow-y-auto divide-y divide-white/5 text-[11px]">
              {Object.values(RESPONSE_CODES).map((entry) => {
                const isCurrent = entry.code === field.rawValue.trim();
                return (
                  <div
                    key={entry.code}
                    className={`py-1.5 px-2 rounded flex items-center justify-between ${
                      isCurrent
                        ? "bg-amber-500/20 text-amber-300 font-bold border border-amber-500/30"
                        : "text-slate-400 hover:text-slate-200"
                    }`}
                  >
                    <span className="w-8 font-bold">{entry.code}</span>
                    <span className="flex-1 truncate">{entry.name}</span>
                    {isCurrent && <span className="text-[10px] text-amber-400">← Selected</span>}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* 2. DE18: Merchant Category Codes */}
        {field.number === 18 && (
          <div className="bg-slate-900/60 border border-white/10 rounded-xl p-3 space-y-2">
            <div className="text-[10px] text-slate-400 uppercase tracking-wider font-bold">
              ISO 18245 MCC Lookup
            </div>
            <div className="max-h-60 overflow-y-auto divide-y divide-white/5 text-[11px]">
              {Object.entries(MERCHANT_CATEGORY_CODES).map(([code, name]) => {
                const isCurrent = code === field.rawValue.trim();
                return (
                  <div
                    key={code}
                    className={`py-1.5 px-2 rounded flex items-center justify-between ${
                      isCurrent
                        ? "bg-teal-500/20 text-teal-300 font-bold border border-teal-500/30"
                        : "text-slate-400 hover:text-slate-200"
                    }`}
                  >
                    <span className="w-12 font-bold">{code}</span>
                    <span className="flex-1 truncate">{name}</span>
                    {isCurrent && <span className="text-[10px] text-teal-400">← Selected</span>}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* 3. DE22: POS Entry Modes */}
        {field.number === 22 && (
          <div className="bg-slate-900/60 border border-white/10 rounded-xl p-3 space-y-2">
            <div className="text-[10px] text-slate-400 uppercase tracking-wider font-bold">
              POS Entry Mode Reference
            </div>
            <div className="max-h-60 overflow-y-auto divide-y divide-white/5 text-[11px]">
              {Object.entries(POS_ENTRY_MODES).map(([code, name]) => {
                const isCurrent = field.rawValue.startsWith(code);
                return (
                  <div
                    key={code}
                    className={`py-1.5 px-2 rounded flex items-center justify-between ${
                      isCurrent
                        ? "bg-teal-500/20 text-teal-300 font-bold border border-teal-500/30"
                        : "text-slate-400 hover:text-slate-200"
                    }`}
                  >
                    <span className="w-8 font-bold">{code}</span>
                    <span className="flex-1 truncate">{name}</span>
                    {isCurrent && <span className="text-[10px] text-teal-400">← Match</span>}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* 4. DE49: Currency Table */}
        {field.number === 49 && (
          <div className="bg-slate-900/60 border border-white/10 rounded-xl p-3 space-y-2">
            <div className="text-[10px] text-slate-400 uppercase tracking-wider font-bold">
              ISO 4217 Currency Lookup
            </div>
            <div className="max-h-60 overflow-y-auto divide-y divide-white/5 text-[11px]">
              {Object.entries(CURRENCY_CODES).map(([code, info]) => {
                const isCurrent = code === field.rawValue.trim();
                return (
                  <div
                    key={code}
                    className={`py-1.5 px-2 rounded flex items-center justify-between ${
                      isCurrent
                        ? "bg-emerald-500/20 text-emerald-300 font-bold border border-emerald-500/30"
                        : "text-slate-400 hover:text-slate-200"
                    }`}
                  >
                    <span className="w-10 font-bold">{code}</span>
                    <span className="flex-1 truncate">
                      {info.code} ({info.symbol}) — {info.name}
                    </span>
                    {isCurrent && <span className="text-[10px] text-emerald-400">← Match</span>}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* 5. DE120: POS Terminal Location Data */}
        {field.number === 120 && (
          <div className="bg-slate-900/60 border border-white/10 rounded-xl p-3 space-y-2.5">
            <div className="text-[10px] text-slate-400 uppercase tracking-wider font-bold flex items-center justify-between">
              <span>POS GPS Coordinates</span>
              <span className="text-teal-400 font-normal">WGS 84</span>
            </div>
            
            {field.subFields && field.subFields.length > 0 && (
              <div className="space-y-1.5 text-[11px]">
                {field.subFields.map((sub, sIdx) => (
                  <div
                    key={sIdx}
                    className="flex items-center justify-between p-2 rounded bg-black/40 border border-white/5"
                  >
                    <span className="text-slate-400">{sub.name}</span>
                    <span className="text-slate-200 font-semibold">{sub.decodedValue}</span>
                  </div>
                ))}
              </div>
            )}

            {/* Direct Google Maps Link */}
            {(() => {
              const mapSub = field.subFields?.find((s) => s.name === "Map Query");
              const url = mapSub ? mapSub.rawValue : `https://www.google.com/maps?q=${field.rawValue.replace(" ", "")}`;
              return (
                <a
                  href={url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center justify-center gap-1.5 w-full py-2 px-3 rounded-lg bg-teal-500/20 hover:bg-teal-500/30 text-teal-300 border border-teal-500/30 text-xs font-semibold transition-all mt-2 text-center"
                >
                  <span>📍 Open in Google Maps</span>
                </a>
              );
            })()}
          </div>
        )}
      </div>
    </div>
  );
};

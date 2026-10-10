"use client";

import React, { useState } from "react";
import {
  ArrowDownLeft,
  ArrowLeft,
  ArrowUpRight,
  ChevronDown,
  ChevronRight,
  Code,
  Copy,
  Eye,
  EyeOff,
  GitCompare,
  Layers,
  Lock,
  Radio,
  Server
} from "lucide-react";
import { DecodedFieldDto, DecodedPacketDto, DecodedTransactionDto } from "@/lib/types";
import { getCategoryBadgeClass } from "@/lib/referenceTables";
import { EmvTlvTree } from "./EmvTlvTree";
import { DiffViewer } from "./DiffViewer";
import { RawXmlViewer } from "./RawXmlViewer";
import { TransactionSummary } from "./TransactionSummary";

interface PacketInspectorProps {
  transaction: DecodedTransactionDto | null;
  selectedField: DecodedFieldDto | null;
  onSelectField: (field: DecodedFieldDto | null) => void;
  /** Shown on small screens, where the inspector replaces the timeline. */
  onBack?: () => void;
}

type TabType = "REQUEST" | "RESPONSE" | "DIFF" | "RAW";

export const PacketInspector: React.FC<PacketInspectorProps> = ({
  transaction,
  selectedField,
  onSelectField,
  onBack,
}) => {
  // The parent remounts this component per transaction (key), so tab and
  // reveal state never carry over to the next transaction.
  const [activeTab, setActiveTab] = useState<TabType>(transaction?.request ? "REQUEST" : "RESPONSE");
  const [revealedFields, setRevealedFields] = useState<Record<number, boolean>>({});
  const [collapsedCategories, setCollapsedCategories] = useState<Record<string, boolean>>({});

  if (!transaction) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center bg-app p-8 text-center text-slate-500 select-none">
        <Radio className="w-10 h-10 text-slate-700 mb-3" />
        <div className="text-base font-semibold text-slate-400">No Transaction Selected</div>
        <div className="text-xs text-slate-500 mt-1 max-w-sm">
          Select a transaction from the timeline on the left to inspect its MTI, ISO 8583 fields, EMV TLV data, and raw packet stream.
        </div>
      </div>
    );
  }

  const req = transaction.request;
  const resp = transaction.response;
  const currentPacket = activeTab === "RESPONSE" ? resp : req;

  // A field selected on one tab doesn't belong to the next one.
  const selectTab = (tab: TabType) => {
    setActiveTab(tab);
    onSelectField(null);
  };

  const toggleCategory = (cat: string) => {
    setCollapsedCategories((prev) => ({ ...prev, [cat]: !prev[cat] }));
  };

  const toggleReveal = (fieldNum: number, e: React.MouseEvent) => {
    e.stopPropagation();
    setRevealedFields((prev) => ({ ...prev, [fieldNum]: !prev[fieldNum] }));
  };

  // Group fields by category
  const groupedFields: Record<string, DecodedFieldDto[]> = {};
  if (currentPacket) {
    currentPacket.fields.forEach((f) => {
      const cat = f.category || "GENERAL";
      if (!groupedFields[cat]) groupedFields[cat] = [];
      groupedFields[cat].push(f);
    });
  }

  const mti = currentPacket?.mti;

  return (
    <div className="flex-1 flex flex-col h-full bg-app overflow-hidden select-none border-r border-white/10">
      {/* Top Packet Header Strip */}
      <div className="px-4 py-2.5 bg-slate-900/90 border-b border-white/10 flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div className="flex items-center gap-2 min-w-0">
          {onBack && (
            <button
              type="button"
              onClick={onBack}
              aria-label="Back to transactions"
              className="lg:hidden p-1.5 rounded-lg bg-slate-800/80 border border-white/10 text-slate-300 hover:text-slate-100"
            >
              <ArrowLeft className="w-4 h-4" />
            </button>
          )}
          {/* Tab Selector */}
          <div className="flex items-center bg-slate-950/80 p-1 rounded-lg border border-white/10 font-mono text-xs min-w-0 overflow-x-auto">
            <button
              onClick={() => selectTab("REQUEST")}
              disabled={!req}
              className={`flex items-center gap-1.5 px-2 sm:px-3 py-1 rounded whitespace-nowrap transition-all ${
                activeTab === "REQUEST"
                  ? "bg-sky-500/20 text-sky-400 font-semibold shadow-sm"
                  : "text-slate-400 hover:text-slate-200 disabled:opacity-30"
              }`}
            >
              <ArrowUpRight className="w-3.5 h-3.5" />
              <span>Request<span className="hidden sm:inline"> ({req?.mti?.raw || "0200"})</span></span>
            </button>

            <button
              onClick={() => selectTab("RESPONSE")}
              disabled={!resp}
              className={`flex items-center gap-1.5 px-2 sm:px-3 py-1 rounded whitespace-nowrap transition-all ${
                activeTab === "RESPONSE"
                  ? "bg-emerald-500/20 text-emerald-400 font-semibold shadow-sm"
                  : "text-slate-400 hover:text-slate-200 disabled:opacity-30"
              }`}
            >
              <ArrowDownLeft className="w-3.5 h-3.5" />
              <span>Response<span className="hidden sm:inline"> ({resp?.mti?.raw || "Pending"})</span></span>
            </button>

            <button
              onClick={() => selectTab("DIFF")}
              disabled={!req && !resp}
              className={`flex items-center gap-1.5 px-2 sm:px-3 py-1 rounded whitespace-nowrap transition-all ${
                activeTab === "DIFF"
                  ? "bg-purple-500/20 text-purple-400 font-semibold shadow-sm"
                  : "text-slate-400 hover:text-slate-200 disabled:opacity-30"
              }`}
            >
              <GitCompare className="w-3.5 h-3.5" />
              <span>Diff</span>
            </button>

            <button
              onClick={() => selectTab("RAW")}
              className={`flex items-center gap-1.5 px-2 sm:px-3 py-1 rounded whitespace-nowrap transition-all ${
                activeTab === "RAW"
                  ? "bg-amber-500/20 text-amber-400 font-semibold shadow-sm"
                  : "text-slate-400 hover:text-slate-200"
              }`}
            >
              <Code className="w-3.5 h-3.5" />
              <span>Raw<span className="hidden sm:inline"> XML</span></span>
            </button>
          </div>
        </div>

        {/* Packet Metadata */}
        {currentPacket && (
          <div className="hidden md:flex items-center gap-3 text-xs font-mono text-slate-400">
            <span className="flex items-center gap-1">
              <Server className="w-3.5 h-3.5 text-slate-500" />
              <span>{currentPacket.realm || "channel/NIBSS"}</span>
            </span>
            <span className="text-slate-600">·</span>
            <span>STAN: <span className="text-slate-200 font-bold">{transaction.stan}</span></span>
            {transaction.serial && (
              <>
                <span className="text-slate-600">·</span>
                <span className="text-sky-400 font-semibold">POS: {transaction.serial}</span>
              </>
            )}
          </div>
        )}
      </div>

      <TransactionSummary transaction={transaction} />

      {/* Main Content Area based on Tab */}
      <div className="flex-1 overflow-y-auto">
        {activeTab === "DIFF" ? (
          <DiffViewer request={req || null} response={resp || null} />
        ) : activeTab === "RAW" ? (
          <RawXmlViewer xml={currentPacket?.rawXml || req?.rawXml || resp?.rawXml || ""} />
        ) : !currentPacket ? (
          <div className="p-8 text-center text-xs text-slate-500 font-mono italic">
            No packet received yet for this phase.
          </div>
        ) : (
          <div className="p-4 space-y-4">
            {/* 1. MTI Interactive Card */}
            {mti && (
              <div className="bg-card/80 border border-white/10 rounded-xl p-4 shadow-lg shadow-black/20">
                <div className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-2.5 flex items-center justify-between">
                  <span>Message Type Indicator (MTI: DE0)</span>
                  <span className="text-sky-400 font-mono font-medium">{mti.label}</span>
                </div>

                {/* 4 MTI Digit Tiles */}
                <div className="grid grid-cols-4 gap-2.5">
                  {[
                    { digit: mti.raw[0] || "0", title: "Version", desc: mti.version },
                    { digit: mti.raw[1] || "2", title: "Class", desc: mti.messageClass },
                    { digit: mti.raw[2] || "0", title: "Function", desc: mti.messageFunction },
                    { digit: mti.raw[3] || "0", title: "Origin", desc: mti.messageOrigin },
                  ].map((tile, i) => (
                    <div
                      key={i}
                      className="bg-slate-900/90 border border-white/10 hover:border-sky-500/40 rounded-lg p-2.5 text-center transition-all group"
                    >
                      <div className="text-xl font-bold font-mono text-white group-hover:text-sky-400 transition-colors">
                        {tile.digit}
                      </div>
                      <div className="text-[10px] font-semibold text-slate-400 uppercase tracking-wide mt-0.5">
                        {tile.title}
                      </div>
                      <div className="text-[11px] font-medium text-sky-300 mt-1 truncate">
                        {tile.desc}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* 2. Grouped ISO Field List */}
            <div className="space-y-3">
              {Object.entries(groupedFields).map(([category, fields]) => {
                const badge = getCategoryBadgeClass(category);
                const isCollapsed = collapsedCategories[category];

                return (
                  <div
                    key={category}
                    className="border border-white/10 rounded-xl overflow-hidden bg-card/40"
                  >
                    {/* Category Header */}
                    <div
                      onClick={() => toggleCategory(category)}
                      className="flex items-center justify-between px-3 py-2 bg-slate-900/70 border-b border-white/5 cursor-pointer hover:bg-slate-800/60 transition-colors"
                    >
                      <div className="flex items-center gap-2">
                        <div className={`w-2.5 h-2.5 rounded-sm ${badge.bar}`} />
                        <span className="text-xs font-bold uppercase tracking-wider text-slate-300">
                          {category.replace("_", " ")}
                        </span>
                        <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-black/40 text-slate-400">
                          {fields.length}
                        </span>
                      </div>
                      {isCollapsed ? (
                        <ChevronRight className="w-4 h-4 text-slate-500" />
                      ) : (
                        <ChevronDown className="w-4 h-4 text-slate-500" />
                      )}
                    </div>

                    {/* Field Rows */}
                    {!isCollapsed && (
                      <div className="divide-y divide-white/5 font-mono text-xs">
                        {fields.map((field) => {
                          const isSelected = selectedField?.number === field.number;
                          const isRevealed = revealedFields[field.number];
                          const isDe55 = field.number === 55;

                          // Format display value
                          let displayValue = field.rawValue;
                          if (field.isSensitive && !isRevealed) {
                            if (field.number === 2 && field.rawValue.length > 4) {
                              displayValue = `•••• •••• •••• ${field.rawValue.slice(-4)}`;
                            } else {
                              displayValue = "••••••••••••••••";
                            }
                          }

                          return (
                            <div
                              key={field.number}
                              onClick={() => onSelectField(field)}
                              className={`p-3 transition-colors cursor-pointer group ${
                                isSelected
                                  ? "bg-slate-800/80 ring-1 ring-inset ring-sky-500/40"
                                  : "hover:bg-slate-800/40"
                              }`}
                            >
                              {/* Field Row Header */}
                              <div className="flex items-center justify-between mb-1.5">
                                <div className="flex items-center gap-2">
                                  <div className={`w-1 h-3.5 rounded ${badge.bar}`} />
                                  <span className="px-1.5 py-0.2 rounded bg-white/10 text-slate-200 font-bold text-[11px]">
                                    DE {field.number}
                                  </span>
                                  <span className="text-xs font-semibold text-slate-200 tracking-tight">
                                    {field.name}
                                  </span>
                                </div>

                                <div className="flex items-center gap-2 text-[10px] text-slate-500">
                                  {field.isSensitive && (
                                    <button
                                      onClick={(e) => toggleReveal(field.number, e)}
                                      className="flex items-center gap-1 px-1.5 py-0.5 rounded bg-rose-500/10 text-rose-400 border border-rose-500/20 hover:bg-rose-500/20 transition-colors"
                                    >
                                      {isRevealed ? (
                                        <>
                                          <EyeOff className="w-3 h-3" />
                                          <span>Mask</span>
                                        </>
                                      ) : (
                                        <>
                                          <Eye className="w-3 h-3" />
                                          <span>Reveal</span>
                                        </>
                                      )}
                                    </button>
                                  )}
                                  <span className="font-mono">{field.isoType} {field.dataType}</span>
                                </div>
                              </div>

                              {/* Primary Decoded Value */}
                              <div className="pl-3 mb-1">
                                {field.decodedValue ? (
                                  <div className="text-sky-300 font-semibold text-xs mb-0.5">
                                    {field.decodedValue}
                                  </div>
                                ) : null}

                                <div className="text-slate-300 font-mono text-xs break-all bg-black/30 px-2 py-1 rounded border border-white/5">
                                  {displayValue}
                                </div>
                              </div>

                              {/* Sub-fields if present */}
                              {field.subFields && field.subFields.length > 0 && (
                                <div className="pl-3 mt-2 grid grid-cols-1 sm:grid-cols-2 gap-1.5">
                                  {field.subFields.map((sub, sIdx) => (
                                    <div
                                      key={sIdx}
                                      className="bg-slate-900/60 border border-white/5 rounded px-2 py-1 text-[11px]"
                                    >
                                      <span className="text-slate-400 mr-1.5">{sub.name}:</span>
                                      <span className="text-slate-200 font-semibold">{sub.decodedValue}</span>
                                    </div>
                                  ))}
                                </div>
                              )}

                              {/* Special DE55 EMV Tree embedded */}
                              {isDe55 && field.tlvEntries && (
                                <div className="pl-3 mt-2">
                                  <EmvTlvTree entries={field.tlvEntries} />
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

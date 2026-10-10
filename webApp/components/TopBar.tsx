"use client";

import React from "react";
import {
  Activity,
  CreditCard,
  Download,
  Filter,
  Layers,
  RefreshCw,
  Search,
  Settings,
  Sparkles,
  Trash2,
  Wifi,
  WifiOff
} from "lucide-react";
import { DeviceSummaryDto } from "@/lib/types";
import { ConnectionState } from "@/lib/connection";

const CONNECTION_LABELS: Record<ConnectionState, string> = {
  LIVE: "LIVE",
  CONNECTING: "CONNECTING",
  OFFLINE: "OFFLINE · RETRYING",
  UNAUTHORIZED: "INVALID KEY",
  NO_KEY: "NO KEY",
};

interface TopBarProps {
  connectionState: ConnectionState;
  selectedSerial: string;
  onSelectSerial: (serial: string) => void;
  devices: DeviceSummaryDto[];
  searchQuery: string;
  onSearchChange: (query: string) => void;
  messageFilter: string;
  onMessageFilterChange: (filter: string) => void;
  onSimulate: () => void;
  onClear: () => void;
  onOpenSettings: () => void;
  onExport: () => void;
  packetCount: number;
}

export const TopBar: React.FC<TopBarProps> = ({
  connectionState,
  selectedSerial,
  onSelectSerial,
  devices,
  searchQuery,
  onSearchChange,
  messageFilter,
  onMessageFilterChange,
  onSimulate,
  onClear,
  onOpenSettings,
  onExport,
  packetCount,
}) => {
  const isConnected = connectionState === "LIVE";

  return (
    <header className="h-14 border-b border-white/10 bg-[#0F172A]/90 backdrop-blur-md px-4 flex items-center justify-between gap-4 select-none z-20">
      {/* Left Branding & Device Selector */}
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2 pr-3 border-r border-white/10">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-sky-500 to-indigo-600 flex items-center justify-center shadow-lg shadow-sky-500/20">
            <CreditCard className="w-4 h-4 text-white" />
          </div>
          <div>
            <div className="flex items-center gap-1.5 font-bold tracking-tight text-white text-sm">
              Tap8583
              <span className="text-[10px] px-1.5 py-0.5 rounded bg-sky-500/20 text-sky-400 font-mono font-medium">
                v2.0 Cloud
              </span>
            </div>
          </div>
        </div>

        {/* Device Selector */}
        <div className="flex items-center gap-2 bg-slate-900/80 border border-white/10 rounded-lg px-2.5 py-1.5">
          <Layers className="w-3.5 h-3.5 text-slate-400" />
          <select
            value={selectedSerial}
            onChange={(e) => onSelectSerial(e.target.value)}
            className="bg-transparent text-xs text-slate-200 font-mono outline-none cursor-pointer pr-1"
          >
            <option value="ALL" className="bg-slate-900 text-slate-200">
              All Devices ({devices.length} active)
            </option>
            {devices.map((d) => (
              <option key={d.serial} value={d.serial} className="bg-slate-900 text-slate-200">
                {d.serial} ({d.transactionCount} txns)
              </option>
            ))}
          </select>
        </div>

        {/* Message Filter Tabs */}
        <div className="hidden lg:flex items-center bg-slate-900/70 p-1 rounded-lg border border-white/5 text-xs font-mono">
          {[
            { id: "ALL", label: "All" },
            { id: "0200", label: "Financial (0200)" },
            { id: "0100", label: "Auth (0100)" },
            { id: "0400", label: "Reversal (0400)" },
            { id: "0800", label: "Network (0800)" },
            { id: "DECLINED", label: "Declined" },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => onMessageFilterChange(tab.id)}
              className={`px-2.5 py-1 rounded transition-all ${
                messageFilter === tab.id
                  ? "bg-sky-500/20 text-sky-300 font-medium shadow-sm"
                  : "text-slate-400 hover:text-slate-200"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Center Search */}
      <div className="flex-1 max-w-md relative">
        <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder="Search STAN, PAN, Amount, RRN, Response code..."
          className="w-full bg-slate-900/80 border border-white/10 focus:border-sky-500/50 rounded-lg pl-9 pr-8 py-1.5 text-xs text-slate-200 placeholder-slate-500 font-mono outline-none transition-all"
        />
        {searchQuery && (
          <button
            onClick={() => onSearchChange("")}
            className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 text-xs"
          >
            ×
          </button>
        )}
      </div>

      {/* Right Controls & Status */}
      <div className="flex items-center gap-2">
        {/* Quick Simulate Button */}
        <button
          onClick={onSimulate}
          title="Inject mock NIBSS ISO 8583 transaction"
          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-indigo-600/20 hover:bg-indigo-600/30 border border-indigo-500/30 text-indigo-300 text-xs font-medium transition-all"
        >
          <Sparkles className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Simulate Txn</span>
        </button>

        {/* Clear Button */}
        <button
          onClick={onClear}
          title="Clear transactions"
          className="p-1.5 rounded-lg bg-slate-800/80 hover:bg-slate-700/80 border border-white/10 text-slate-400 hover:text-slate-200 text-xs transition-all"
        >
          <Trash2 className="w-3.5 h-3.5" />
        </button>

        {/* Export Button */}
        <button
          onClick={onExport}
          title="Export transactions as JSON"
          className="p-1.5 rounded-lg bg-slate-800/80 hover:bg-slate-700/80 border border-white/10 text-slate-400 hover:text-slate-200 text-xs transition-all"
        >
          <Download className="w-3.5 h-3.5" />
        </button>

        {/* Settings Button */}
        <button
          onClick={onOpenSettings}
          title="Settings & Connection"
          className="p-1.5 rounded-lg bg-slate-800/80 hover:bg-slate-700/80 border border-white/10 text-slate-400 hover:text-slate-200 text-xs transition-all"
        >
          <Settings className="w-3.5 h-3.5" />
        </button>

        {/* Live Status Pill */}
        <div
          title={connectionState === "UNAUTHORIZED" || connectionState === "NO_KEY" ? "Open settings to enter your API key" : undefined}
          className={`flex items-center gap-2 px-2.5 py-1 rounded-full text-xs font-mono border ${
            isConnected
              ? "bg-emerald-500/10 border-emerald-500/30 text-emerald-400"
              : "bg-rose-500/10 border-rose-500/30 text-rose-400"
          }`}
        >
          <span className="relative flex h-2 w-2">
            {isConnected && (
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
            )}
            <span
              className={`relative inline-flex rounded-full h-2 w-2 ${
                isConnected ? "bg-emerald-500" : "bg-rose-500"
              }`}
            ></span>
          </span>
          <span className="hidden sm:inline font-medium">
            {CONNECTION_LABELS[connectionState]}
          </span>
          <span className="px-1.5 py-0.2 rounded bg-black/30 text-[10px] text-slate-300">
            {packetCount}
          </span>
        </div>
      </div>
    </header>
  );
};

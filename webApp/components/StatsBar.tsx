"use client";

import React, { useEffect, useRef, useState } from "react";
import { Bell, BellOff, Plug } from "lucide-react";
import { ChannelEventDto } from "@/lib/types";
import { TransactionStats } from "@/lib/stats";
import { channelEventLabel } from "@/lib/channelEvents";

interface StatsBarProps {
  stats: TransactionStats;
  channelEvents: ChannelEventDto[];
  alertsEnabled: boolean;
  onToggleAlerts: () => void;
  /** Hides the alerts toggle (e.g. for a decoded log, which never alerts). */
  showAlertsToggle?: boolean;
}

const Stat: React.FC<{ label: string; value: string; tone?: string; title?: string }> = ({
  label,
  value,
  tone = "text-slate-100",
  title,
}) => (
  <div className="flex items-baseline gap-1.5 flex-shrink-0" title={title}>
    <span className="text-[11px] text-slate-400">{label}</span>
    <span className={`text-xs font-mono font-semibold ${tone}`}>{value}</span>
  </div>
);

/** One-line health summary of the transactions in view. */
export const StatsBar: React.FC<StatsBarProps> = ({
  stats,
  channelEvents,
  alertsEnabled,
  onToggleAlerts,
  showAlertsToggle = true,
}) => {
  const [showChannel, setShowChannel] = useState(false);
  const popoverRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!showChannel) return;
    const onClick = (e: MouseEvent) => {
      if (popoverRef.current && !popoverRef.current.contains(e.target as Node)) setShowChannel(false);
    };
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, [showChannel]);

  const ms = (v: number | null) => (v == null ? "—" : `${v} ms`);

  return (
    <div className="relative flex items-center gap-2 px-3 sm:px-4 py-1.5 border-b border-white/10 bg-panel select-none">
      <div className="flex-1 min-w-0 flex items-center gap-4 overflow-x-auto">
        <Stat label="Total" value={String(stats.total)} />
        <Stat
          label="Approval"
          value={stats.approvalRate == null ? "—" : `${stats.approvalRate}%`}
          tone={stats.approvalRate == null ? "text-slate-400" : stats.approvalRate >= 90 ? "text-emerald-400" : stats.approvalRate >= 70 ? "text-amber-400" : "text-rose-400"}
          title="Approved share of transactions that finished (approved, declined, timed out or errored)"
        />
        <Stat label="Approved" value={String(stats.approved)} tone="text-emerald-400" />
        <Stat label="Declined" value={String(stats.declined)} tone={stats.declined ? "text-rose-400" : "text-slate-400"} />
        <Stat
          label="No response / error"
          value={String(stats.failed)}
          tone={stats.failed ? "text-amber-400" : "text-slate-400"}
        />
        <Stat label="Avg RTT" value={ms(stats.avgRttMs)} />
        <Stat label="p95 RTT" value={ms(stats.p95RttMs)} title="95% of round trips were at or under this" />
      </div>

      <div className="flex items-center gap-1.5 flex-shrink-0" ref={popoverRef}>
        <button
          type="button"
          onClick={() => setShowChannel((v) => !v)}
          aria-expanded={showChannel}
          title="Timeouts, disconnects and failed connects reported by terminals"
          className={`flex items-center gap-1 px-2 py-0.5 rounded-md border text-[11px] font-medium ${
            channelEvents.length
              ? "border-orange-500/30 bg-orange-500/10 text-orange-400 hover:bg-orange-500/20"
              : "border-white/10 text-slate-400 hover:text-slate-200"
          }`}
        >
          <Plug className="w-3 h-3" />
          <span className="hidden sm:inline">Channel errors</span>
          <span className="font-mono">{channelEvents.length}</span>
        </button>
        {showAlertsToggle && (
          <button
            type="button"
            onClick={onToggleAlerts}
            aria-pressed={alertsEnabled}
            title={alertsEnabled ? "Pop-up alerts are on" : "Pop-up alerts are off"}
            aria-label={alertsEnabled ? "Turn pop-up alerts off" : "Turn pop-up alerts on"}
            className="p-1 rounded-md border border-white/10 text-slate-400 hover:text-slate-200"
          >
            {alertsEnabled ? <Bell className="w-3.5 h-3.5" /> : <BellOff className="w-3.5 h-3.5" />}
          </button>
        )}

        {showChannel && (
          <div className="absolute right-3 top-full mt-1 z-30 w-[min(24rem,calc(100vw-1.5rem))] max-h-80 overflow-y-auto rounded-xl border border-white/10 bg-raised shadow-2xl p-2">
            {channelEvents.length === 0 ? (
              <div className="p-3 text-xs text-slate-400">
                No channel errors. Timeouts, disconnects and failed connects from terminals will show here.
              </div>
            ) : (
              [...channelEvents].reverse().map((ev) => (
                <div key={ev.id} className="px-2 py-1.5 rounded-lg hover:bg-white/5 text-xs">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-semibold text-orange-400">{channelEventLabel(ev)}</span>
                    <span className="font-mono text-[11px] text-slate-400">
                      {new Date(ev.capturedAt).toLocaleTimeString([], { hour12: false })}
                    </span>
                  </div>
                  <div className="text-slate-400 font-mono text-[11px] break-words">
                    {[ev.serial, ev.realm, ev.message].filter(Boolean).join(" · ")}
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
};

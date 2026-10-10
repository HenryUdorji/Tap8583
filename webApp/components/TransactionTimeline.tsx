"use client";

import React, { useEffect, useRef, useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  Clock,
  HelpCircle,
  Pause,
  Play,
  Radio,
  Timer,
  XCircle,
} from "lucide-react";
import { DecodedTransactionDto } from "@/lib/types";
import { STATUS_STYLES, TransactionStatus } from "@/lib/transactionStatus";
import {
  formatAmount,
  panLast4,
  requestMti,
  responseCode,
  transactionType,
} from "@/lib/transactionSummary";

/** How long the server waits for a response before marking a request TIMEOUT. */
const MATCH_WINDOW_SECONDS = 60;

const STATUS_ICONS: Record<TransactionStatus, React.ComponentType<{ className?: string }>> = {
  APPROVED: CheckCircle2,
  DECLINED: XCircle,
  PENDING: Clock,
  TIMEOUT: Timer,
  NETWORK_ERROR: AlertTriangle,
  ORPHAN: HelpCircle,
};

interface TransactionTimelineProps {
  transactions: DecodedTransactionDto[];
  selectedTransactionId: string | null;
  onSelectTransaction: (tx: DecodedTransactionDto) => void;
  isPaused: boolean;
  onTogglePause: () => void;
  /** Transactions that arrived while paused and are not shown yet. */
  heldCount: number;
}

function capturedAt(tx: DecodedTransactionDto): Date | null {
  const iso = tx.request?.capturedAt || tx.response?.capturedAt || tx.createdAt;
  const date = iso ? new Date(iso) : null;
  return date && !Number.isNaN(date.getTime()) ? date : null;
}

function relativeTime(date: Date, now: number): string {
  const seconds = Math.max(0, Math.round((now - date.getTime()) / 1000));
  if (seconds < 5) return "just now";
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return date.toLocaleDateString();
}

export const TransactionTimeline: React.FC<TransactionTimelineProps> = ({
  transactions,
  selectedTransactionId,
  onSelectTransaction,
  isPaused,
  onTogglePause,
  heldCount,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [autoScroll, setAutoScroll] = useState(true);
  const [unreadCount, setUnreadCount] = useState(0);
  const prevCountRef = useRef(transactions.length);
  const [now, setNow] = useState(() => Date.now());

  // Tick once a second so relative times and pending countdowns stay current.
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    if (transactions.length > prevCountRef.current) {
      if (autoScroll && containerRef.current) {
        containerRef.current.scrollTop = 0;
      } else {
        setUnreadCount((prev) => prev + (transactions.length - prevCountRef.current));
      }
    }
    prevCountRef.current = transactions.length;
  }, [transactions, autoScroll]);

  const handleScroll = () => {
    if (!containerRef.current) return;
    const isAtTop = containerRef.current.scrollTop < 20;
    setAutoScroll(isAtTop);
    if (isAtTop) setUnreadCount(0);
  };

  const scrollToTop = () => {
    if (containerRef.current) {
      containerRef.current.scrollTo({ top: 0, behavior: "smooth" });
      setAutoScroll(true);
      setUnreadCount(0);
    }
  };

  return (
    <div className="flex flex-col h-full bg-panel border-r border-white/10 relative select-none">
      {/* Panel Header */}
      <div className="px-4 py-2.5 border-b border-white/10 flex items-center justify-between gap-2 bg-slate-900/60">
        <div className="flex items-center gap-2 min-w-0">
          <Radio className={`w-3.5 h-3.5 ${isPaused ? "text-amber-400" : "text-sky-400"}`} />
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-300 truncate">
            Transactions
          </span>
          <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-white/5 text-slate-400">
            {transactions.length}
          </span>
        </div>
        <button
          type="button"
          onClick={onTogglePause}
          aria-pressed={isPaused}
          title={isPaused ? "Resume the live stream" : "Pause the live stream to read without the list moving"}
          className={`flex items-center gap-1.5 px-2 py-1 rounded-lg border text-xs font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-500/60 ${
            isPaused
              ? "bg-amber-500/10 border-amber-500/30 text-amber-400 hover:bg-amber-500/20"
              : "bg-slate-800/80 border-white/10 text-slate-300 hover:text-slate-100"
          }`}
        >
          {isPaused ? <Play className="w-3.5 h-3.5" /> : <Pause className="w-3.5 h-3.5" />}
          <span>{isPaused ? (heldCount > 0 ? `Resume · ${heldCount} new` : "Resume") : "Pause"}</span>
        </button>
      </div>

      {/* Unread Pill */}
      {unreadCount > 0 && (
        <button
          onClick={scrollToTop}
          className="absolute top-12 left-1/2 -translate-x-1/2 z-30 bg-sky-500 hover:bg-sky-400 text-slate-950 px-3 py-1 rounded-full text-xs font-semibold shadow-lg shadow-sky-500/20 flex items-center gap-1.5 transition-colors"
        >
          <span>↑ {unreadCount} new</span>
        </button>
      )}

      {/* Transaction List */}
      <div
        ref={containerRef}
        onScroll={handleScroll}
        className="flex-1 overflow-y-auto p-3 space-y-2.5"
      >
        {transactions.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center p-6 text-center text-slate-500">
            <Radio className="w-8 h-8 text-slate-600 mb-2 animate-pulse" />
            <div className="text-sm font-medium text-slate-400">Listening for transactions…</div>
            <div className="text-xs text-slate-500 mt-1 max-w-xs">
              Perform a transaction on your POS terminal, or use &quot;Simulate Txn&quot; in the top bar.
            </div>
          </div>
        ) : (
          transactions.map((tx) => {
            const isSelected = tx.id === selectedTransactionId;
            const style = STATUS_STYLES[tx.status];
            const StatusIcon = STATUS_ICONS[tx.status];
            const mti = requestMti(tx);
            const amount = formatAmount(tx);
            const last4 = panLast4(tx);
            const rc = responseCode(tx);
            const date = capturedAt(tx);
            const waited = date ? Math.floor((now - date.getTime()) / 1000) : null;

            const outcome =
              tx.status === "PENDING" && waited !== null
                ? `Waiting ${Math.min(waited, MATCH_WINDOW_SECONDS)}s / ${MATCH_WINDOW_SECONDS}s`
                : rc
                ? `${rc.code} · ${rc.name}`
                : style.label;

            return (
              <button
                type="button"
                key={tx.id}
                onClick={() => onSelectTransaction(tx)}
                aria-current={isSelected ? "true" : undefined}
                className={`group relative w-full text-left rounded-xl border p-3 transition-colors duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-500/60 ${
                  isSelected
                    ? "bg-slate-800/90 border-sky-500/80 shadow-lg shadow-sky-500/10 ring-1 ring-sky-500/50"
                    : "bg-card/70 hover:bg-slate-800/60 border-white/10 hover:border-white/20"
                }`}
              >
                {/* Left accent border */}
                <div className={`absolute left-0 top-3 bottom-3 w-1 rounded-r ${style.bar}`} />

                {/* Top row: time and amount */}
                <div className="flex items-center justify-between gap-2 mb-1 pl-2">
                  <span className="text-[11px] text-slate-400 font-mono" title={date?.toLocaleString()}>
                    {date ? `${date.toLocaleTimeString([], { hour12: false })} · ${relativeTime(date, now)}` : "—"}
                  </span>
                  {amount && (
                    <span className="font-mono text-xs font-semibold text-slate-100">{amount}</span>
                  )}
                </div>

                {/* Middle row: type, card and MTI */}
                <div className="flex items-center justify-between gap-2 mb-2 pl-2">
                  <span className="text-sm font-semibold text-slate-100 truncate">
                    {transactionType(tx)}
                    {last4 && <span className="font-mono font-normal text-slate-400"> ••{last4}</span>}
                  </span>
                  {mti && (
                    <span className="font-mono text-[11px] font-bold text-slate-300 px-1.5 py-0.5 rounded bg-white/10 flex-shrink-0">
                      {mti}
                    </span>
                  )}
                </div>

                {/* Bottom row: outcome and RTT */}
                <div className="pt-2 border-t border-white/5 flex items-center justify-between gap-2 pl-2">
                  <div className={`flex items-center gap-1.5 text-xs min-w-0 ${style.text}`}>
                    <StatusIcon className="w-3.5 h-3.5 flex-shrink-0" />
                    <span className="text-[11px] font-medium truncate">{outcome}</span>
                  </div>

                  {tx.rttMs != null ? (
                    <span
                      title="Round-trip time"
                      className={`text-[11px] font-mono px-1.5 py-0.5 rounded font-medium border flex-shrink-0 ${
                        tx.rttMs < 500
                          ? "bg-emerald-500/10 text-emerald-400 border-emerald-500/20"
                          : tx.rttMs < 2000
                          ? "bg-amber-500/10 text-amber-400 border-amber-500/20"
                          : "bg-rose-500/10 text-rose-400 border-rose-500/20"
                      }`}
                    >
                      {tx.rttMs} ms
                    </span>
                  ) : (
                    <span className="text-[11px] text-slate-500 font-mono truncate flex-shrink-0 max-w-[45%]">
                      {tx.serial || ""}
                    </span>
                  )}
                </div>
              </button>
            );
          })
        )}
      </div>
    </div>
  );
};

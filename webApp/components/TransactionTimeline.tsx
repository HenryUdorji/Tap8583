"use client";

import React, { useEffect, useRef, useState } from "react";
import {
  ArrowDownLeft,
  ArrowUpRight,
  CheckCircle2,
  Clock,
  HelpCircle,
  Radio,
  Timer,
  XCircle
} from "lucide-react";
import { DecodedTransactionDto } from "@/lib/types";

interface TransactionTimelineProps {
  transactions: DecodedTransactionDto[];
  selectedTransactionId: string | null;
  onSelectTransaction: (tx: DecodedTransactionDto) => void;
}

export const TransactionTimeline: React.FC<TransactionTimelineProps> = ({
  transactions,
  selectedTransactionId,
  onSelectTransaction,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [autoScroll, setAutoScroll] = useState(true);
  const [unreadCount, setUnreadCount] = useState(0);
  const prevCountRef = useRef(transactions.length);

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

  // Helper to extract display data from transaction
  const getCardDetails = (tx: DecodedTransactionDto) => {
    const req = tx.request;
    const resp = tx.response;

    const reqMti = req?.mti?.raw || (resp?.mti?.raw ? "0" + (parseInt(resp.mti.raw) - 10) : "0200");
    const respMti = resp?.mti?.raw || "Pending";

    const panField = req?.fields.find((f) => f.number === 2) || resp?.fields.find((f) => f.number === 2);
    const amountField = req?.fields.find((f) => f.number === 4) || resp?.fields.find((f) => f.number === 4);
    const timeField = req?.fields.find((f) => f.number === 7) || req?.fields.find((f) => f.number === 12);
    const entryModeField = req?.fields.find((f) => f.number === 22);
    const respCodeField = resp?.fields.find((f) => f.number === 39);

    // Format PAN with masked dots
    let maskedPan = "•••• •••• •••• ••••";
    if (panField?.rawValue) {
      const raw = panField.rawValue;
      if (raw.length > 4) {
        maskedPan = `•••• •••• •••• ${raw.slice(-4)}`;
      }
    }

    // Format Amount
    let formattedAmount = "₦ 0.00";
    if (amountField?.decodedValue) {
      formattedAmount = amountField.decodedValue;
    } else if (amountField?.rawValue) {
      const minor = parseInt(amountField.rawValue, 10);
      formattedAmount = `₦ ${(minor / 100).toLocaleString("en-NG", { minimumFractionDigits: 2 })}`;
    }

    return {
      reqMti,
      respMti,
      maskedPan,
      formattedAmount,
      entryMode: entryModeField?.decodedValue || "POS",
      respCode: respCodeField?.rawValue || (resp ? "00" : null),
      respMeaning: respCodeField?.decodedValue || (resp ? "Approved" : "Awaiting response..."),
      capturedAt: req?.capturedAt || resp?.capturedAt || tx.createdAt || "",
    };
  };

  return (
    <div className="flex flex-col h-full bg-[#0B101D] border-r border-white/10 relative select-none">
      {/* Panel Header */}
      <div className="px-4 py-2.5 border-b border-white/10 flex items-center justify-between bg-slate-900/60">
        <div className="flex items-center gap-2">
          <Radio className="w-3.5 h-3.5 text-sky-400" />
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-300">
            Transaction Timeline
          </span>
        </div>
        <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-white/5 text-slate-400">
          {transactions.length} Total
        </span>
      </div>

      {/* Unread Pill */}
      {unreadCount > 0 && (
        <button
          onClick={scrollToTop}
          className="absolute top-12 left-1/2 -translate-x-1/2 z-30 bg-sky-500 hover:bg-sky-400 text-slate-950 px-3 py-1 rounded-full text-xs font-semibold shadow-lg shadow-sky-500/20 flex items-center gap-1.5 transition-all animate-bounce"
        >
          <span>↓ {unreadCount} new</span>
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
              Perform a transaction on your Android POS terminal or click &quot;Simulate Txn&quot; in the top bar.
            </div>
          </div>
        ) : (
          transactions.map((tx) => {
            const isSelected = tx.id === selectedTransactionId;
            const details = getCardDetails(tx);
            const isApproved = tx.status === "APPROVED";
            const isDeclined = tx.status === "DECLINED";
            const isPending = tx.status === "PENDING";
            const isTimeout = tx.status === "TIMEOUT";

            return (
              <div
                key={tx.id}
                onClick={() => onSelectTransaction(tx)}
                className={`group relative rounded-xl border p-3 cursor-pointer transition-all duration-200 ${
                  isSelected
                    ? "bg-slate-800/90 border-sky-500/80 shadow-lg shadow-sky-500/10 ring-1 ring-sky-500/50"
                    : "bg-[#11192E]/70 hover:bg-slate-800/60 border-white/10 hover:border-white/20"
                }`}
              >
                {/* Left accent border */}
                <div
                  className={`absolute left-0 top-3 bottom-3 w-1 rounded-r ${
                    isApproved
                      ? "bg-emerald-500"
                      : isDeclined
                      ? "bg-rose-500"
                      : isTimeout
                      ? "bg-amber-500"
                      : "bg-sky-500"
                  }`}
                />

                {/* Top Row: MTI & Amount */}
                <div className="flex items-center justify-between mb-1.5 pl-2">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs font-bold text-white px-1.5 py-0.5 rounded bg-white/10">
                      {details.reqMti}
                    </span>
                    {tx.serial && (
                      <span className="text-[10px] font-mono text-slate-400 px-1 rounded bg-slate-900 border border-white/5">
                        {tx.serial}
                      </span>
                    )}
                  </div>
                  <div className="font-mono text-xs font-semibold text-emerald-400">
                    {details.formattedAmount}
                  </div>
                </div>

                {/* PAN and STAN */}
                <div className="flex items-center justify-between text-xs text-slate-300 font-mono mb-2 pl-2">
                  <span className="tracking-wider">{details.maskedPan}</span>
                  <span className="text-[11px] text-slate-400">STAN: {tx.stan}</span>
                </div>

                {/* Bottom Row: Response & RTT */}
                <div className="pt-2 border-t border-white/5 flex items-center justify-between pl-2">
                  <div className="flex items-center gap-1.5 text-xs">
                    {isApproved && <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />}
                    {isDeclined && <XCircle className="w-3.5 h-3.5 text-rose-400" />}
                    {isPending && <Clock className="w-3.5 h-3.5 text-sky-400 animate-spin" />}
                    {isTimeout && <Timer className="w-3.5 h-3.5 text-amber-400" />}

                    <span
                      className={`text-[11px] font-medium truncate max-w-[140px] ${
                        isApproved
                          ? "text-emerald-400"
                          : isDeclined
                          ? "text-rose-400"
                          : isTimeout
                          ? "text-amber-400"
                          : "text-slate-400"
                      }`}
                    >
                      {details.respCode ? `${details.respCode} ${details.respMeaning}` : details.respMeaning}
                    </span>
                  </div>

                  {/* RTT Badge */}
                  {tx.rttMs != null ? (
                    <span
                      className={`text-[10px] font-mono px-1.5 py-0.5 rounded font-medium ${
                        tx.rttMs < 500
                          ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                          : tx.rttMs < 2000
                          ? "bg-amber-500/10 text-amber-400 border border-amber-500/20"
                          : "bg-rose-500/10 text-rose-400 border border-rose-500/20"
                      }`}
                    >
                      +{tx.rttMs}ms {tx.rttMs > 2000 && "🐢"}
                    </span>
                  ) : (
                    <span className="text-[10px] text-slate-500 font-mono">
                      {details.capturedAt ? details.capturedAt.split("T")[1]?.slice(0, 8) : ""}
                    </span>
                  )}
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};

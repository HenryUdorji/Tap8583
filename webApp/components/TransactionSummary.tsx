"use client";

import React from "react";
import { AlertTriangle, CheckCircle2, Clock, HelpCircle, Lightbulb, Timer, XCircle } from "lucide-react";
import { DecodedTransactionDto } from "@/lib/types";
import { STATUS_STYLES, TransactionStatus } from "@/lib/transactionStatus";
import { describeTransaction, responseCode } from "@/lib/transactionSummary";
import { RESPONSE_CODES, RESPONSE_HINTS } from "@/lib/referenceTables";

const STATUS_ICONS: Record<TransactionStatus, React.ComponentType<{ className?: string }>> = {
  APPROVED: CheckCircle2,
  DECLINED: XCircle,
  PENDING: Clock,
  TIMEOUT: Timer,
  NETWORK_ERROR: AlertTriangle,
  ORPHAN: HelpCircle,
};

/** Status-level explanations for outcomes that have no response code to explain them. */
const STATUS_HINTS: Partial<Record<TransactionStatus, string>> = {
  TIMEOUT:
    "No response arrived within 60 seconds. If the terminal also gave up, it should send a reversal (0420) for this STAN.",
  ORPHAN:
    "This response arrived without a matching request from the same device and STAN in the last 60 seconds. The request may have been sent before the listener started, or the STAN changed.",
};

/**
 * Plain-language header for a transaction: what happened, the outcome, and
 * for failures, what to check first.
 */
export const TransactionSummary: React.FC<{ transaction: DecodedTransactionDto }> = ({ transaction }) => {
  const style = STATUS_STYLES[transaction.status];
  const Icon = STATUS_ICONS[transaction.status];
  const rc = responseCode(transaction);
  const failed = transaction.status !== "APPROVED" && transaction.status !== "PENDING";
  const hint = failed ? (rc && RESPONSE_HINTS[rc.code]) || STATUS_HINTS[transaction.status] : undefined;
  const meaning = rc ? RESPONSE_CODES[rc.code]?.description : undefined;

  return (
    <div className="px-4 py-3 border-b border-white/10 bg-panel space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full border text-xs font-semibold ${style.badge}`}>
          <Icon className="w-3.5 h-3.5" />
          {rc ? `${rc.code} · ${rc.name}` : style.label}
        </span>
        {transaction.rttMs != null && (
          <span className="text-xs font-mono text-slate-400">{transaction.rttMs} ms round trip</span>
        )}
      </div>
      <p className="text-sm text-slate-100 font-medium">
        {describeTransaction(transaction)}
        {meaning && <span className="text-slate-400 font-normal"> · {meaning}</span>}
      </p>
      {hint && (
        <div className="flex items-start gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-slate-200">
          <Lightbulb className="w-3.5 h-3.5 mt-0.5 flex-shrink-0 text-amber-400" />
          <span>
            <span className="font-semibold text-amber-400">What to check: </span>
            {hint}
          </span>
        </div>
      )}
    </div>
  );
};

"use client";

import React from "react";
import {
  AlertTriangle,
  CheckCircle2,
  Clock,
  HelpCircle,
  Lightbulb,
  Plug,
  Timer,
  Undo2,
  XCircle,
} from "lucide-react";
import { ChannelEventDto, DecodedTransactionDto } from "@/lib/types";
import { STATUS_STYLES, TransactionStatus } from "@/lib/transactionStatus";
import { describeTransaction, requestMti, responseCode, transactionType } from "@/lib/transactionSummary";
import { originalReference } from "@/lib/reversals";
import { channelEventLabel } from "@/lib/channelEvents";
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

interface TransactionSummaryProps {
  transaction: DecodedTransactionDto;
  /** Channel problems on this device while the request was outstanding. */
  channelEvents?: ChannelEventDto[];
  /** For a reversal: the transaction it reverses, when it is in view. */
  original?: DecodedTransactionDto | null;
  /** For an original: reversals of it. */
  reversals?: DecodedTransactionDto[];
  onSelectTransaction?: (tx: DecodedTransactionDto) => void;
}

function outcome(tx: DecodedTransactionDto): string {
  const rc = responseCode(tx);
  return rc ? `${rc.code} · ${rc.name}` : STATUS_STYLES[tx.status].label;
}

function secondsAfter(laterIso: string, earlierIso: string | undefined): string | null {
  if (!earlierIso) return null;
  const ms = Date.parse(laterIso) - Date.parse(earlierIso);
  return Number.isNaN(ms) ? null : `${(ms / 1000).toFixed(1)} s after the request`;
}

/** One linked transaction with a button that opens it. */
const LinkedTransaction: React.FC<{
  prefix: string;
  tx: DecodedTransactionDto;
  onSelect?: (tx: DecodedTransactionDto) => void;
}> = ({ prefix, tx, onSelect }) => (
  <div className="flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg border border-purple-500/30 bg-purple-500/10 px-3 py-2 text-xs text-slate-200">
    <Undo2 className="w-3.5 h-3.5 flex-shrink-0 text-purple-400" />
    <span>
      <span className="font-semibold text-purple-400">{prefix} </span>
      {transactionType(tx)} · {requestMti(tx) || "?"} · STAN {tx.stan || "?"} ·{" "}
      <span className={STATUS_STYLES[tx.status].text}>{outcome(tx)}</span>
    </span>
    {onSelect && (
      <button
        type="button"
        onClick={() => onSelect(tx)}
        className="ml-auto px-2 py-0.5 rounded border border-purple-500/30 text-purple-400 hover:bg-purple-500/20 font-medium"
      >
        Open
      </button>
    )}
  </div>
);

/**
 * Plain-language header for a transaction: what happened, the outcome, and
 * for failures, what to check first.
 */
export const TransactionSummary: React.FC<TransactionSummaryProps> = ({
  transaction,
  channelEvents = [],
  original,
  reversals = [],
  onSelectTransaction,
}) => {
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
      {channelEvents.length > 0 && (
        <div className="flex items-start gap-2 rounded-lg border border-orange-500/30 bg-orange-500/10 px-3 py-2 text-xs text-slate-200">
          <Plug className="w-3.5 h-3.5 mt-0.5 flex-shrink-0 text-orange-400" />
          <div className="space-y-0.5 min-w-0">
            <span className="font-semibold text-orange-400">The terminal&apos;s channel reported: </span>
            {channelEvents.map((ev) => (
              <div key={ev.id} className="break-words">
                {channelEventLabel(ev)}
                {ev.message && <span className="text-slate-400 font-mono"> · {ev.message}</span>}
                {secondsAfter(ev.capturedAt, transaction.request?.capturedAt) && (
                  <span className="text-slate-400"> · {secondsAfter(ev.capturedAt, transaction.request?.capturedAt)}</span>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
      {original && <LinkedTransaction prefix="Reverses:" tx={original} onSelect={onSelectTransaction} />}
      {!original && originalReference(transaction) && (
        <div className="flex items-center gap-2 rounded-lg border border-purple-500/30 bg-purple-500/10 px-3 py-2 text-xs text-slate-300">
          <Undo2 className="w-3.5 h-3.5 flex-shrink-0 text-purple-400" />
          The original transaction (STAN {originalReference(transaction)!.stan}) is not in the current list.
        </div>
      )}
      {reversals.map((r) => (
        <LinkedTransaction key={r.id} prefix="Reversed by:" tx={r} onSelect={onSelectTransaction} />
      ))}
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

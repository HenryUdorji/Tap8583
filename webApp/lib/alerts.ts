import { ChannelEventDto, DecodedTransactionDto } from "@/lib/types";
import { channelEventLabel } from "@/lib/channelEvents";
import { responseCode, transactionType } from "@/lib/transactionSummary";

export interface Alert {
  /** Stable per transaction and outcome, so one outcome alerts once. */
  id: string;
  transactionId?: string;
  level: "error" | "warning";
  title: string;
  detail: string;
}

/** Round trips slower than this raise a warning. */
export const SLOW_RTT_MS = 3_000;

/**
 * Decline codes that usually mean an integration problem rather than a
 * cardholder problem: worth interrupting someone for.
 */
const INTEGRATION_CODES = new Set(["06", "12", "30", "58", "63"]);

export function alertForTransaction(tx: DecodedTransactionDto): Alert | null {
  const label = `${transactionType(tx)} · STAN ${tx.stan || "?"}${tx.serial ? ` · ${tx.serial}` : ""}`;
  const rc = responseCode(tx);

  if (tx.status === "TIMEOUT") {
    return { id: `${tx.id}:TIMEOUT`, transactionId: tx.id, level: "error", title: "No response from the host", detail: label };
  }
  if (tx.status === "NETWORK_ERROR") {
    return {
      id: `${tx.id}:NETWORK_ERROR`,
      transactionId: tx.id,
      level: "error",
      title: rc ? `${rc.code} · ${rc.name}` : "Host or network error",
      detail: label,
    };
  }
  if (tx.status === "DECLINED" && rc && INTEGRATION_CODES.has(rc.code)) {
    return {
      id: `${tx.id}:${rc.code}`,
      transactionId: tx.id,
      level: "warning",
      title: `${rc.code} · ${rc.name}`,
      detail: `${label}. Usually a message format or setup problem.`,
    };
  }
  if (tx.rttMs != null && tx.rttMs > SLOW_RTT_MS) {
    return {
      id: `${tx.id}:SLOW`,
      transactionId: tx.id,
      level: "warning",
      title: `Slow response: ${(tx.rttMs / 1000).toFixed(1)} s`,
      detail: label,
    };
  }
  return null;
}

export function alertForChannelEvent(ev: ChannelEventDto): Alert {
  return {
    id: `channel:${ev.id}`,
    level: "error",
    title: channelEventLabel(ev),
    detail: [ev.serial, ev.message].filter(Boolean).join(" · "),
  };
}

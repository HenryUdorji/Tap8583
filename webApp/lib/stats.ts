import { DecodedTransactionDto } from "@/lib/types";

export interface TransactionStats {
  total: number;
  approved: number;
  declined: number;
  /** Timeouts and host or network errors. */
  failed: number;
  pending: number;
  /** Approved share of finished transactions, 0–100, or null when none finished. */
  approvalRate: number | null;
  avgRttMs: number | null;
  p95RttMs: number | null;
}

export function computeStats(transactions: DecodedTransactionDto[]): TransactionStats {
  let approved = 0;
  let declined = 0;
  let failed = 0;
  let pending = 0;
  const rtts: number[] = [];

  for (const tx of transactions) {
    if (tx.status === "APPROVED") approved++;
    else if (tx.status === "DECLINED") declined++;
    else if (tx.status === "TIMEOUT" || tx.status === "NETWORK_ERROR") failed++;
    else if (tx.status === "PENDING") pending++;
    if (tx.rttMs != null && tx.rttMs >= 0) rtts.push(tx.rttMs);
  }

  const finished = approved + declined + failed;
  rtts.sort((a, b) => a - b);
  const p95Index = Math.min(rtts.length - 1, Math.ceil(rtts.length * 0.95) - 1);

  return {
    total: transactions.length,
    approved,
    declined,
    failed,
    pending,
    approvalRate: finished > 0 ? Math.round((approved / finished) * 100) : null,
    avgRttMs: rtts.length > 0 ? Math.round(rtts.reduce((a, b) => a + b, 0) / rtts.length) : null,
    p95RttMs: rtts.length > 0 ? rtts[p95Index] : null,
  };
}

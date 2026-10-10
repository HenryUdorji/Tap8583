import { DecodedTransactionDto } from "@/lib/types";
import { packetField } from "@/lib/transactionSummary";

/** True for 04xx messages (reversals and reversal advices). */
export function isReversal(tx: DecodedTransactionDto): boolean {
  const mti = tx.request?.mti?.raw || tx.response?.mti?.raw || "";
  return mti[1] === "4";
}

export interface OriginalReference {
  /** Original MTI from DE90, when the reversal carries it. */
  mti: string | null;
  stan: string;
}

/**
 * What a reversal points at. DE90 (original data elements) starts with the
 * original MTI (4) and STAN (6); without DE90, terminals usually reuse the
 * original STAN.
 */
export function originalReference(tx: DecodedTransactionDto): OriginalReference | null {
  if (!isReversal(tx)) return null;
  const de90 = packetField(tx.request || tx.response, 90)?.rawValue?.trim();
  if (de90 && /^\d{10}/.test(de90)) return { mti: de90.slice(0, 4), stan: de90.slice(4, 10) };
  return tx.stan ? { mti: null, stan: tx.stan } : null;
}

export interface ReversalLinks {
  /** Reversal id → the transaction it reverses. */
  originalOf: Map<string, DecodedTransactionDto>;
  /** Original id → reversals of it, oldest first. */
  reversalsOf: Map<string, DecodedTransactionDto[]>;
}

function time(tx: DecodedTransactionDto): number {
  const iso = tx.request?.capturedAt || tx.response?.capturedAt || tx.createdAt;
  const t = iso ? Date.parse(iso) : NaN;
  return Number.isNaN(t) ? 0 : t;
}

/** Links each reversal to the latest earlier non-reversal with the same device and STAN. */
export function linkReversals(transactions: DecodedTransactionDto[]): ReversalLinks {
  const originalOf = new Map<string, DecodedTransactionDto>();
  const reversalsOf = new Map<string, DecodedTransactionDto[]>();

  const candidatesByKey = new Map<string, DecodedTransactionDto[]>();
  for (const tx of transactions) {
    if (isReversal(tx) || !tx.request) continue;
    const key = `${tx.serial || ""}:${tx.stan}`;
    const list = candidatesByKey.get(key);
    if (list) list.push(tx);
    else candidatesByKey.set(key, [tx]);
  }

  for (const reversal of transactions) {
    const ref = originalReference(reversal);
    if (!ref) continue;
    const candidates = (candidatesByKey.get(`${reversal.serial || ""}:${ref.stan}`) || []).filter(
      (c) => !ref.mti || c.request?.mti?.raw === ref.mti
    );
    if (candidates.length === 0) continue;
    const at = time(reversal);
    const earlier = candidates.filter((c) => time(c) <= at);
    const pool = earlier.length > 0 ? earlier : candidates;
    const original = pool.reduce((best, c) => (time(c) > time(best) ? c : best));

    originalOf.set(reversal.id, original);
    const list = reversalsOf.get(original.id) || [];
    list.push(reversal);
    list.sort((a, b) => time(a) - time(b));
    reversalsOf.set(original.id, list);
  }

  return { originalOf, reversalsOf };
}

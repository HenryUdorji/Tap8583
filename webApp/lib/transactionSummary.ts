import { DecodedFieldDto, DecodedPacketDto, DecodedTransactionDto } from "@/lib/types";
import { CURRENCY_CODES, RESPONSE_CODES } from "@/lib/referenceTables";

/** Transaction type from the first two digits of DE3 (processing code). */
const PROCESSING_TYPES: Record<string, string> = {
  "00": "Purchase",
  "01": "Cash withdrawal",
  "09": "Purchase with cashback",
  "20": "Refund",
  "21": "Deposit",
  "28": "Payment",
  "31": "Balance enquiry",
  "40": "Transfer",
  "50": "Bill payment",
  "92": "Key exchange",
  "9A": "Key exchange",
  "9B": "Key exchange",
  "9C": "Key exchange",
  "9D": "Parameter download",
  "9G": "Key exchange",
};

/** Message class from the second MTI digit, used when there is no DE3. */
const MTI_CLASSES: Record<string, string> = {
  "1": "Authorization",
  "2": "Financial transaction",
  "3": "File action",
  "4": "Reversal",
  "5": "Reconciliation",
  "6": "Administrative message",
  "8": "Network management",
};

function field(packet: DecodedPacketDto | null | undefined, n: number): DecodedFieldDto | undefined {
  return packet?.fields.find((f) => f.number === n);
}

function eitherField(tx: DecodedTransactionDto, n: number): DecodedFieldDto | undefined {
  return field(tx.request, n) || field(tx.response, n);
}

/** "₦ 500.00" from DE4 + DE49, using the server's decode when present. */
export function formatAmount(tx: DecodedTransactionDto): string | null {
  const amount = eitherField(tx, 4);
  if (!amount) return null;
  if (amount.decodedValue) return amount.decodedValue;
  const minor = parseInt(amount.rawValue, 10);
  if (Number.isNaN(minor)) return amount.rawValue;
  const currency = CURRENCY_CODES[eitherField(tx, 49)?.rawValue || ""];
  const decimals = currency?.decimals ?? 2;
  const value = (minor / 10 ** decimals).toLocaleString(undefined, {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
  return currency ? `${currency.symbol} ${value}` : value;
}

/** Last four digits of the PAN, e.g. "2753", or null when there is no card. */
export function panLast4(tx: DecodedTransactionDto): string | null {
  const pan = eitherField(tx, 2)?.rawValue;
  return pan && pan.length > 4 ? pan.slice(-4) : null;
}

/** "Verve" from the server's DE2 decode ("Verve · 19 digits · …"). */
export function cardScheme(tx: DecodedTransactionDto): string | null {
  const decoded = eitherField(tx, 2)?.decodedValue;
  const scheme = decoded?.split("·")[0]?.trim();
  return scheme && scheme !== "Unknown" ? scheme : null;
}

export function transactionType(tx: DecodedTransactionDto): string {
  const code = eitherField(tx, 3)?.rawValue?.slice(0, 2).toUpperCase();
  if (code && PROCESSING_TYPES[code]) return PROCESSING_TYPES[code];
  const mti = tx.request?.mti?.raw || tx.response?.mti?.raw || "";
  return MTI_CLASSES[mti[1]] || "Transaction";
}

/** The request MTI, inferred from the response MTI for orphan responses. */
export function requestMti(tx: DecodedTransactionDto): string | null {
  if (tx.request?.mti?.raw) return tx.request.mti.raw;
  const resp = tx.response?.mti?.raw;
  if (!resp || resp.length !== 4) return null;
  const fn = parseInt(resp[2], 10);
  return Number.isNaN(fn) ? resp : `${resp.slice(0, 2)}${Math.max(fn - 1, 0)}${resp[3]}`;
}

export interface ResponseInfo {
  code: string;
  name: string;
}

/** DE39 from the response, or null when there is no response or no DE39. */
export function responseCode(tx: DecodedTransactionDto): ResponseInfo | null {
  const raw = field(tx.response, 39)?.rawValue?.trim();
  if (!raw) return null;
  return { code: raw, name: RESPONSE_CODES[raw]?.name || "Unknown response code" };
}

/**
 * One plain-language sentence, e.g.
 * "Purchase of ₦ 500.00 on Verve card ••2753 at terminal 2214OTGF".
 */
export function describeTransaction(tx: DecodedTransactionDto): string {
  const parts = [transactionType(tx)];
  const amount = formatAmount(tx);
  if (amount) parts.push(`of ${amount}`);
  const last4 = panLast4(tx);
  if (last4) parts.push(`on ${cardScheme(tx) ? `${cardScheme(tx)} card` : "card"} ••${last4}`);
  const terminal = eitherField(tx, 41)?.rawValue?.trim();
  if (terminal) parts.push(`at terminal ${terminal}`);
  return parts.join(" ");
}

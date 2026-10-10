import { DecodedTransactionDto } from "@/lib/types";

export type TransactionStatus = DecodedTransactionDto["status"];

export interface StatusStyle {
  /** Short label shown next to the icon. */
  label: string;
  /** Accent bar / dot colour. */
  bar: string;
  /** Text colour for the label. */
  text: string;
  /** Tinted background + border for badges and banners. */
  badge: string;
}

/** One visual treatment per status, shared by the timeline and the summary card. */
export const STATUS_STYLES: Record<TransactionStatus, StatusStyle> = {
  APPROVED: {
    label: "Approved",
    bar: "bg-emerald-500",
    text: "text-emerald-400",
    badge: "bg-emerald-500/10 border-emerald-500/30 text-emerald-400",
  },
  DECLINED: {
    label: "Declined",
    bar: "bg-rose-500",
    text: "text-rose-400",
    badge: "bg-rose-500/10 border-rose-500/30 text-rose-400",
  },
  PENDING: {
    label: "Awaiting response",
    bar: "bg-sky-500",
    text: "text-sky-400",
    badge: "bg-sky-500/10 border-sky-500/30 text-sky-400",
  },
  TIMEOUT: {
    label: "No response (timed out)",
    bar: "bg-amber-500",
    text: "text-amber-400",
    badge: "bg-amber-500/10 border-amber-500/30 text-amber-400",
  },
  NETWORK_ERROR: {
    label: "Host or network error",
    bar: "bg-orange-500",
    text: "text-orange-400",
    badge: "bg-orange-500/10 border-orange-500/30 text-orange-400",
  },
  ORPHAN: {
    label: "Response without a request",
    bar: "bg-purple-500",
    text: "text-purple-400",
    badge: "bg-purple-500/10 border-purple-500/30 text-purple-400",
  },
};

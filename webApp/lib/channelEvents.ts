import { ChannelEventDto, DecodedTransactionDto } from "@/lib/types";

/** Plain-language names for the channel problems jPOS reports. */
export const CHANNEL_EVENT_LABELS: Record<string, string> = {
  "io-timeout": "Read timed out",
  "peer-disconnect": "Host closed the connection",
  "connect-failed": "Could not connect to the host",
  exception: "Channel error",
};

export function channelEventLabel(ev: ChannelEventDto): string {
  return CHANNEL_EVENT_LABELS[ev.kind] || ev.kind;
}

/** How long after a request a channel problem is still attributed to it. */
const ATTRIBUTION_WINDOW_MS = 65_000;

/**
 * Channel problems on the same device between a request and its response
 * (or up to the match window when no response came).
 */
export function channelEventsFor(tx: DecodedTransactionDto, events: ChannelEventDto[]): ChannelEventDto[] {
  if (!tx.request || events.length === 0) return [];
  const start = Date.parse(tx.request.capturedAt);
  if (Number.isNaN(start)) return [];
  const responseAt = tx.response ? Date.parse(tx.response.capturedAt) : NaN;
  const end = Number.isNaN(responseAt) ? start + ATTRIBUTION_WINDOW_MS : responseAt;
  return events.filter((ev) => {
    if ((ev.serial || "") !== (tx.serial || "")) return false;
    const at = Date.parse(ev.capturedAt);
    return at >= start && at <= end;
  });
}

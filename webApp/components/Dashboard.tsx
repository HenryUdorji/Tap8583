"use client";

import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { FileText } from "lucide-react";
import {
  ChannelEventDto,
  DecodeLogResponse,
  DecodedFieldDto,
  DecodedTransactionDto,
  DeviceSummaryDto,
  WebSocketMessage
} from "@/lib/types";
import { TopBar } from "@/components/TopBar";
import { TransactionAnnotation, TransactionTimeline } from "@/components/TransactionTimeline";
import { PacketInspector } from "@/components/PacketInspector";
import { FieldDetailPanel } from "@/components/FieldDetailPanel";
import { TransactionSummary } from "@/components/TransactionSummary";
import { StatsBar } from "@/components/StatsBar";
import { AlertToasts } from "@/components/AlertToasts";
import { DecodeLogModal } from "@/components/DecodeLogModal";
import { linkReversals } from "@/lib/reversals";
import { channelEventLabel, channelEventsFor } from "@/lib/channelEvents";
import { computeStats } from "@/lib/stats";
import { Alert, alertForChannelEvent, alertForTransaction } from "@/lib/alerts";
import {
  ConnectionState,
  loadAlertsEnabled,
  saveAlertsEnabled,
  WS_CLOSE_UNAUTHORIZED,
  streamUrl,
} from "@/lib/connection";
import { Access, accessFetch, streamAuthFrame } from "@/lib/api";

interface DashboardProps {
  serverUrl: string;
  /** Null until a viewer key is entered (shared-key mode). */
  access: Access | null;
  /** Bump to reconnect, e.g. after the same key is saved again. */
  connectNonce?: number;
  /** The server refused the key or session. */
  onUnauthorized: () => void;
  onOpenSettings: () => void;
  /** Extra control after the logo, e.g. the workspace switcher. */
  topBarExtra?: React.ReactNode;
  /** Shown in the empty timeline, e.g. "Connect a terminal". */
  emptyAction?: { label: string; onClick: () => void };
  /** Wording for Clear's confirmation: who else loses the data. */
  clearAudience?: string;
}

/** The live transaction view: timeline, inspector, field detail, stats and alerts. */
export function Dashboard({
  serverUrl,
  access,
  connectNonce = 0,
  onUnauthorized,
  onOpenSettings,
  topBarExtra,
  emptyAction,
  clearAudience = "everyone viewing this server",
}: DashboardProps) {
  const [connectionState, setConnectionState] = useState<ConnectionState>(access ? "CONNECTING" : "NO_KEY");
  const [devices, setDevices] = useState<DeviceSummaryDto[]>([]);
  const [selectedSerial, setSelectedSerial] = useState<string>("ALL");

  const [transactions, setTransactions] = useState<DecodedTransactionDto[]>([]);
  const [selectedTransactionId, setSelectedTransactionId] = useState<string | null>(null);
  const [selectedField, setSelectedField] = useState<DecodedFieldDto | null>(null);

  const [searchQuery, setSearchQuery] = useState("");
  const [messageFilter, setMessageFilter] = useState("ALL");

  // While paused, the timeline keeps showing (and updating) only the
  // transactions it had when paused; newer ones are held back and counted.
  const [pausedIds, setPausedIds] = useState<Set<string> | null>(null);

  // Below the lg breakpoint the timeline and inspector share the screen in turn.
  const [mobileView, setMobileView] = useState<"list" | "detail">("list");

  // Timeouts, disconnects and failed connects reported by terminals.
  const [channelEvents, setChannelEvents] = useState<ChannelEventDto[]>([]);

  // Pop-up alerts for live problems.
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [alertsEnabled, setAlertsEnabled] = useState(true);
  const alertsEnabledRef = useRef(true);
  const seenAlertIdsRef = useRef<Set<string>>(new Set());

  // A pasted or uploaded log replaces the live list until "Back to live".
  const [decodedLog, setDecodedLog] = useState<(DecodeLogResponse & { name: string }) | null>(null);
  const [isDecodeOpen, setIsDecodeOpen] = useState(false);

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Load preferences from localStorage on mount
  useEffect(() => {
    const savedAlerts = loadAlertsEnabled();
    setAlertsEnabled(savedAlerts);
    alertsEnabledRef.current = savedAlerts;
  }, []);

  // A different key or workspace means a different set of transactions.
  const accessKey = access ? (access.mode === "key" ? `key:${access.apiKey}` : `ws:${access.workspaceId}`) : "";
  useEffect(() => {
    setTransactions([]);
    setChannelEvents([]);
    setDevices([]);
    setSelectedTransactionId(null);
    setSelectedField(null);
    setPausedIds(null);
    setDecodedLog(null);
    setSelectedSerial("ALL");
    if (!access) setConnectionState("NO_KEY");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessKey]);

  const unauthorizedRef = useRef(onUnauthorized);
  unauthorizedRef.current = onUnauthorized;

  const pushAlert = useCallback((alert: Alert | null) => {
    if (!alert || !alertsEnabledRef.current || seenAlertIdsRef.current.has(alert.id)) return;
    seenAlertIdsRef.current.add(alert.id);
    setAlerts((prev) => [...prev, alert].slice(-3));
  }, []);

  const toggleAlerts = () => {
    const next = !alertsEnabled;
    setAlertsEnabled(next);
    alertsEnabledRef.current = next;
    saveAlertsEnabled(next);
    if (!next) setAlerts([]);
  };

  // Connect to WebSocket backend. The key goes in the first frame, never the URL.
  useEffect(() => {
    if (!serverUrl || !access) return;
    let active = true;

    const connectWebSocket = () => {
      if (wsRef.current) {
        wsRef.current.close();
      }
      setConnectionState("CONNECTING");

      try {
        const ws = new WebSocket(streamUrl(serverUrl));
        wsRef.current = ws;

        ws.onopen = () => {
          if (!active) return;
          ws.send(streamAuthFrame(access, selectedSerial));
        };

        ws.onmessage = (event) => {
          if (!active) return;
          try {
            const data: WebSocketMessage = JSON.parse(event.data);

            if (data.type === "INIT_SNAPSHOT") {
              setConnectionState("LIVE");
              if (data.transactions) {
                const newestFirst = [...data.transactions].reverse();
                setTransactions(newestFirst);
                if (newestFirst.length > 0) {
                  setSelectedTransactionId((prev) => prev || newestFirst[0].id);
                }
              }
              if (data.devices) {
                setDevices(data.devices);
              }
              setChannelEvents(data.channelEvents || []);
            } else if (data.type === "CHANNEL_EVENT" && data.channelEvent) {
              const ev = data.channelEvent;
              setChannelEvents((prev) => [...prev, ev].slice(-200));
              pushAlert(alertForChannelEvent(ev));
            } else if (data.type === "TRANSACTION_UPDATED" && data.transaction) {
              const updated = data.transaction;
              setTransactions((prev) => {
                const idx = prev.findIndex((t) => t.id === updated.id);
                if (idx >= 0) {
                  const copy = [...prev];
                  copy[idx] = updated;
                  return copy;
                } else {
                  return [updated, ...prev];
                }
              });

              // Add/update device in list
              if (updated.serial) {
                setDevices((prev) => {
                  const exists = prev.find((d) => d.serial === updated.serial);
                  if (!exists) {
                    return [
                      { serial: updated.serial!, lastSeenAt: new Date().toISOString(), transactionCount: 1 },
                      ...prev,
                    ];
                  }
                  return prev;
                });
              }

              // Auto-select if nothing is selected
              setSelectedTransactionId((prev) => prev || updated.id);
              pushAlert(alertForTransaction(updated));
            } else if (data.type === "CLEAR") {
              if (data.serial) {
                setTransactions((prev) => prev.filter((t) => t.serial !== data.serial));
                setDevices((prev) => prev.filter((d) => d.serial !== data.serial));
                setChannelEvents((prev) => prev.filter((e) => e.serial !== data.serial));
              } else {
                setTransactions([]);
                setDevices([]);
                setChannelEvents([]);
              }
              setSelectedTransactionId(null);
              setSelectedField(null);
            }
          } catch (e) {
            console.error("Error parsing WebSocket message:", e);
          }
        };

        ws.onclose = (event) => {
          if (!active) return;
          if (event.code === WS_CLOSE_UNAUTHORIZED) {
            setConnectionState("UNAUTHORIZED");
            unauthorizedRef.current();
            return;
          }
          setConnectionState("OFFLINE");
          reconnectTimeoutRef.current = setTimeout(connectWebSocket, 3000);
        };

        ws.onerror = () => {
          if (!active) return;
          ws.close();
        };
      } catch (e) {
        console.error("WebSocket connection failed:", e);
        setConnectionState("OFFLINE");
        reconnectTimeoutRef.current = setTimeout(connectWebSocket, 4000);
      }
    };

    connectWebSocket();

    return () => {
      active = false;
      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      if (wsRef.current) wsRef.current.close();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [serverUrl, accessKey, selectedSerial, connectNonce, pushAlert]);

  // Escape closes the field detail drawer.
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") setSelectedField(null);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  // A decoded log, when one is open, stands in for the live data.
  const sourceTransactions = decodedLog ? decodedLog.transactions : transactions;
  const sourceChannelEvents = decodedLog ? decodedLog.channelEvents : channelEvents;
  const deviceFilter = !decodedLog && selectedSerial && selectedSerial !== "ALL" ? selectedSerial : null;

  // Selected Transaction Object
  const selectedTransaction = useMemo(() => {
    return sourceTransactions.find((t) => t.id === selectedTransactionId) || sourceTransactions[0] || null;
  }, [sourceTransactions, selectedTransactionId]);

  // Filtered transactions based on search query, device selector, and message filter
  const filteredTransactions = useMemo(() => {
    return sourceTransactions.filter((tx) => {
      // 1. Device filter
      if (deviceFilter && tx.serial !== deviceFilter) {
        return false;
      }

      // 2. Message type filter
      if (messageFilter !== "ALL") {
        if (messageFilter === "DECLINED") {
          if (tx.status !== "DECLINED") return false;
        } else {
          const reqMti = tx.request?.mti?.raw || "";
          const respMti = tx.response?.mti?.raw || "";
          if (!reqMti.includes(messageFilter) && !respMti.includes(messageFilter)) {
            return false;
          }
        }
      }

      // 3. Search query
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase().trim();
        const stanMatch = tx.stan?.toLowerCase().includes(query);
        const serialMatch = tx.serial?.toLowerCase().includes(query);
        const rttMatch = tx.rttMs?.toString().includes(query);
        const reqFieldsMatch = tx.request?.fields.some(
          (f) =>
            f.rawValue.toLowerCase().includes(query) ||
            (f.decodedValue && f.decodedValue.toLowerCase().includes(query)) ||
            f.name.toLowerCase().includes(query)
        );
        const respFieldsMatch = tx.response?.fields.some(
          (f) =>
            f.rawValue.toLowerCase().includes(query) ||
            (f.decodedValue && f.decodedValue.toLowerCase().includes(query)) ||
            f.name.toLowerCase().includes(query)
        );

        return stanMatch || serialMatch || rttMatch || reqFieldsMatch || respFieldsMatch;
      }

      return true;
    });
  }, [sourceTransactions, deviceFilter, messageFilter, searchQuery]);

  const visibleTransactions = useMemo(
    () => (pausedIds ? filteredTransactions.filter((t) => pausedIds.has(t.id)) : filteredTransactions),
    [filteredTransactions, pausedIds]
  );
  const heldCount = filteredTransactions.length - visibleTransactions.length;

  const visibleChannelEvents = useMemo(
    () => (deviceFilter ? sourceChannelEvents.filter((e) => e.serial === deviceFilter) : sourceChannelEvents),
    [sourceChannelEvents, deviceFilter]
  );
  const stats = useMemo(() => computeStats(filteredTransactions), [filteredTransactions]);
  const reversalLinks = useMemo(() => linkReversals(sourceTransactions), [sourceTransactions]);

  const annotations = useMemo(() => {
    const map = new Map<string, TransactionAnnotation>();
    for (const tx of visibleTransactions) {
      const reversedBy = reversalLinks.reversalsOf.get(tx.id)?.length;
      const issue = channelEventsFor(tx, sourceChannelEvents)[0];
      if (reversedBy || issue) {
        map.set(tx.id, { reversedBy, channelIssue: issue ? channelEventLabel(issue) : undefined });
      }
    }
    return map;
  }, [visibleTransactions, reversalLinks, sourceChannelEvents]);

  const selectTransaction = (tx: DecodedTransactionDto) => {
    setSelectedTransactionId(tx.id);
    setSelectedField(null);
    setMobileView("detail");
  };

  const togglePause = () => {
    setPausedIds((prev) => (prev ? null : new Set(transactions.map((t) => t.id))));
  };

  // Handler: Inject simulated test transactions
  const handleSimulate = async () => {
    const randomStan = Math.floor(100000 + Math.random() * 900000).toString();
    const randomSerial = selectedSerial !== "ALL" ? selectedSerial : "PAX-A920-847291";

    const sampleRequestXml = `
<visualizer-event realm="channel/196.6.103.18:4018" at="${new Date().toISOString()}" lifespan="19ms" tag="send" serial="${randomSerial}">
  <isomsg direction="outgoing">
    <field id="0" value="0200"/>
    <field id="2" value="5061240207416432753"/>
    <field id="3" value="000000"/>
    <field id="4" value="000000050000"/>
    <field id="7" value="0823120530"/>
    <field id="11" value="${randomStan}"/>
    <field id="18" value="6012"/>
    <field id="22" value="051"/>
    <field id="32" value="506124"/>
    <field id="35" value="5061240207416432753D2702601013660902"/>
    <field id="37" value="614114086503"/>
    <field id="41" value="2214OTGF"/>
    <field id="42" value="P332600027425"/>
    <field id="49" value="566"/>
    <field id="52" value="26B7D3B168E69591" type="binary"/>
    <field id="55" value="820258008407A0000003710001950502800480009A032608239F02060000000500009F2608B08F298D5A26BECD9F2701809F360204599F37046A04A1435F340101" type="binary"/>
    <field id="59" value="2214OTGF-P332600027425-614114086503"/>
    <field id="120" value="06.5244,003.3792"/>
    <field id="123" value="51010151134C101"/>
    <field id="128" value="6CFDE6E02F8F5C2203B06B5F8E1AA5C8B649D7A7456A2F721EC39A55B44BC871" type="binary"/>
  </isomsg>
</visualizer-event>
---END---
`;

    const sampleResponseXml = `
<visualizer-event realm="channel/196.6.103.18:4018" at="${new Date(Date.now() + 420).toISOString()}" lifespan="24ms" tag="receive" serial="${randomSerial}">
  <isomsg direction="incoming">
    <field id="0" value="0210"/>
    <field id="2" value="5061240207416432753"/>
    <field id="3" value="000000"/>
    <field id="4" value="000000050000"/>
    <field id="7" value="0823120530"/>
    <field id="11" value="${randomStan}"/>
    <field id="37" value="614114086503"/>
    <field id="38" value="482901"/>
    <field id="39" value="00"/>
    <field id="41" value="2214OTGF"/>
    <field id="44" value="ND"/>
    <field id="49" value="566"/>
    <field id="55" value="820258008407A00000037100019F2608B08F298D5A26BECD9F270140" type="binary"/>
    <field id="128" value="6CFDE6E02F8F5C2203B06B5F8E1AA5C8B649D7A7456A2F721EC39A55B44BC871" type="binary"/>
  </isomsg>
</visualizer-event>
---END---
`;

    if (!access) return;
    const send = (body: string) =>
      accessFetch(serverUrl, access, "/events", {
        method: "POST",
        headers: { "Content-Type": "application/xml", "x-device-serial": randomSerial },
        body,
      });

    try {
      // 1. Send Request
      await send(sampleRequestXml);

      // 2. Wait 450ms and Send Response (simulating network roundtrip)
      setTimeout(() => send(sampleResponseXml), 450);
    } catch (e) {
      console.error("Simulation error:", e);
    }
  };

  // Handler: decode a pasted or uploaded log. Returns an error message, or null on success.
  const handleDecodeLog = async (text: string, name: string): Promise<string | null> => {
    if (!access) return "Enter your viewer key in settings first. Decoding runs on your Tap8583 server.";
    try {
      const res = await accessFetch(serverUrl, access, "/decode", {
        method: "POST",
        headers: { "Content-Type": "text/plain" },
        body: text,
      });
      if (res.status === 401) return "The server didn't accept your sign-in. Sign in again or check your key in settings.";
      if (res.status === 413) return "That log is larger than 2 MB. Trim it to the part around the transactions you need.";
      if (!res.ok) return `The server could not decode this log (HTTP ${res.status}).`;
      const data: DecodeLogResponse = await res.json();
      if (data.transactions.length === 0 && data.channelEvents.length === 0) {
        return data.errors[0]
          ? `No messages could be decoded. First problem: ${data.errors[0]}`
          : "No jPOS messages were found. The decoder looks for <log>, <isomsg> or <visualizer-event> blocks.";
      }
      const newestFirst = [...data.transactions].reverse();
      setDecodedLog({ ...data, transactions: newestFirst, name });
      setPausedIds(null);
      setSelectedTransactionId(newestFirst[0]?.id ?? null);
      setSelectedField(null);
      setMobileView("list");
      return null;
    } catch {
      return "Could not reach the Tap8583 server.";
    }
  };

  const backToLive = () => {
    setDecodedLog(null);
    setSelectedTransactionId(transactions[0]?.id ?? null);
    setSelectedField(null);
  };

  // Handler: Clear session
  const handleClear = async () => {
    const scope = selectedSerial !== "ALL" ? `device ${selectedSerial}` : "all devices";
    if (!access) return;
    if (!window.confirm(`Delete the transactions for ${scope}? This clears them for ${clearAudience}.`)) {
      return;
    }
    try {
      const query = selectedSerial !== "ALL" ? `?serial=${encodeURIComponent(selectedSerial)}` : "";
      const res = await accessFetch(serverUrl, access, `/clear${query}`, { method: "POST" });
      if (!res.ok) {
        console.error("Clear failed:", res.status);
        return;
      }
      // The server broadcasts CLEAR, which updates the list.
      setSelectedTransactionId(null);
      setSelectedField(null);
    } catch (e) {
      console.error("Clear error:", e);
    }
  };

  // Handler: Export transactions as JSON
  const handleExport = () => {
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(filteredTransactions, null, 2));
    const downloadAnchor = document.createElement("a");
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", `tap8583-export-${new Date().toISOString().slice(0, 10)}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  return (
    <div className="flex flex-col h-screen overflow-hidden bg-app text-slate-100 font-sans">
      {/* 1. Fixed Top Toolbar */}
      <TopBar
        connectionState={connectionState}
        selectedSerial={selectedSerial}
        onSelectSerial={setSelectedSerial}
        devices={devices}
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        messageFilter={messageFilter}
        onMessageFilterChange={setMessageFilter}
        onSimulate={handleSimulate}
        onClear={handleClear}
        onOpenSettings={onOpenSettings}
        extra={topBarExtra}
        onExport={handleExport}
        onOpenDecodeLog={() => setIsDecodeOpen(true)}
        packetCount={filteredTransactions.length}
      />

      {decodedLog && (
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 sm:px-4 py-2 border-b border-sky-500/30 bg-sky-500/10 text-xs text-slate-200">
          <FileText className="w-3.5 h-3.5 text-sky-400 flex-shrink-0" />
          <span className="min-w-0">
            <span className="font-semibold">Viewing {decodedLog.name}:</span>{" "}
            {decodedLog.transactions.length} transaction{decodedLog.transactions.length === 1 ? "" : "s"}
            {decodedLog.channelEvents.length > 0 &&
              `, ${decodedLog.channelEvents.length} channel error${decodedLog.channelEvents.length === 1 ? "" : "s"}`}
            {decodedLog.truncated && " (only the first 2,000 events)"}
            {decodedLog.errors.length > 0 && `. Some blocks could not be read: ${decodedLog.errors[0]}`}
          </span>
          <button
            type="button"
            onClick={backToLive}
            className="ml-auto px-2.5 py-1 rounded-lg bg-sky-500 hover:bg-sky-400 text-on-accent font-semibold"
          >
            Back to live
          </button>
        </div>
      )}

      <StatsBar
        stats={stats}
        channelEvents={visibleChannelEvents}
        alertsEnabled={alertsEnabled}
        onToggleAlerts={toggleAlerts}
        showAlertsToggle={!decodedLog}
      />

      {/* 2. Main Content Area: timeline · inspector · field detail */}
      <div className="flex-1 flex overflow-hidden">
        {/* Timeline: full width on small screens until a transaction is opened */}
        <div
          className={`${
            mobileView === "detail" ? "hidden lg:block" : "block"
          } w-full lg:w-80 xl:w-96 flex-shrink-0 h-full`}
        >
          <TransactionTimeline
            transactions={visibleTransactions}
            selectedTransactionId={selectedTransaction?.id || null}
            onSelectTransaction={selectTransaction}
            isPaused={pausedIds !== null}
            onTogglePause={decodedLog ? undefined : togglePause}
            heldCount={heldCount}
            annotations={annotations}
            onOpenDecodeLog={decodedLog ? undefined : () => setIsDecodeOpen(true)}
            emptyAction={decodedLog ? undefined : emptyAction}
            title={decodedLog ? "Decoded log" : "Transactions"}
          />
        </div>

        {/* Packet Inspector */}
        <div className={`${mobileView === "list" ? "hidden lg:flex" : "flex"} flex-1 min-w-0 h-full`}>
          <PacketInspector
            key={selectedTransaction?.id || "none"}
            transaction={selectedTransaction}
            selectedField={selectedField}
            onSelectField={(field) => setSelectedField(field)}
            onBack={() => setMobileView("list")}
            summary={
              selectedTransaction && (
                <TransactionSummary
                  transaction={selectedTransaction}
                  channelEvents={channelEventsFor(selectedTransaction, sourceChannelEvents)}
                  original={reversalLinks.originalOf.get(selectedTransaction.id)}
                  reversals={reversalLinks.reversalsOf.get(selectedTransaction.id)}
                  onSelectTransaction={selectTransaction}
                />
              )
            }
          />
        </div>

        {/* Field detail: a column on wide screens… */}
        <div className="hidden xl:block w-96 flex-shrink-0 h-full">
          <FieldDetailPanel field={selectedField} />
        </div>

        {/* …and a slide-over drawer below that */}
        {selectedField && (
          <div className="xl:hidden fixed inset-0 z-40 flex justify-end" role="dialog" aria-modal="true" aria-label="Field details">
            <button
              type="button"
              aria-label="Close field details"
              className="absolute inset-0 bg-black/50"
              onClick={() => setSelectedField(null)}
            />
            <div className="relative w-full sm:w-96 h-full shadow-2xl">
              <FieldDetailPanel field={selectedField} onClose={() => setSelectedField(null)} />
            </div>
          </div>
        )}
      </div>

      <DecodeLogModal isOpen={isDecodeOpen} onClose={() => setIsDecodeOpen(false)} onDecode={handleDecodeLog} />

      <AlertToasts
        alerts={alerts}
        onDismiss={(id) => setAlerts((prev) => prev.filter((a) => a.id !== id))}
        onOpen={(alert) => {
          setAlerts((prev) => prev.filter((a) => a.id !== alert.id));
          if (!alert.transactionId) return;
          setDecodedLog(null);
          setSelectedTransactionId(alert.transactionId);
          setSelectedField(null);
          setMobileView("detail");
        }}
      />

    </div>
  );
}

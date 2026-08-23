"use client";

import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  DecodedFieldDto,
  DecodedTransactionDto,
  DeviceSummaryDto,
  WebSocketMessage
} from "@/lib/types";
import { TopBar } from "@/components/TopBar";
import { TransactionTimeline } from "@/components/TransactionTimeline";
import { PacketInspector } from "@/components/PacketInspector";
import { FieldDetailPanel } from "@/components/FieldDetailPanel";
import { SettingsModal } from "@/components/SettingsModal";

export default function Home() {
  const [apiKey, setApiKey] = useState(process.env.NEXT_PUBLIC_TAP8583_API_KEY || "tap_live_secret");
  const [serverUrl, setServerUrl] = useState(process.env.NEXT_PUBLIC_SERVER_WS_URL || "ws://localhost:8080/ws/stream");
  const [httpEndpoint, setHttpEndpoint] = useState(process.env.NEXT_PUBLIC_SERVER_HTTP_URL || "http://localhost:8080/api/v1/events");

  const [isConnected, setIsConnected] = useState(false);
  const [devices, setDevices] = useState<DeviceSummaryDto[]>([]);
  const [selectedSerial, setSelectedSerial] = useState<string>("ALL");

  const [transactions, setTransactions] = useState<DecodedTransactionDto[]>([]);
  const [selectedTransactionId, setSelectedTransactionId] = useState<string | null>(null);
  const [selectedField, setSelectedField] = useState<DecodedFieldDto | null>(null);

  const [searchQuery, setSearchQuery] = useState("");
  const [messageFilter, setMessageFilter] = useState("ALL");
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Load preferences from localStorage on mount
  useEffect(() => {
    if (typeof window !== "undefined") {
      const savedKey = localStorage.getItem("tap8583_api_key");
      if (savedKey) setApiKey(savedKey);

      const savedUrl = localStorage.getItem("tap8583_server_url");
      if (savedUrl) {
        setServerUrl(savedUrl);
        const httpUrl = savedUrl
          .replace("ws://", "http://")
          .replace("wss://", "https://")
          .replace("/ws/stream", "/api/v1/events");
        setHttpEndpoint(httpUrl);
      } else if (process.env.NEXT_PUBLIC_SERVER_WS_URL) {
        setServerUrl(process.env.NEXT_PUBLIC_SERVER_WS_URL);
      }
    }
  }, []);

  // Connect to WebSocket backend
  useEffect(() => {
    let active = true;

    const connectWebSocket = () => {
      if (wsRef.current) {
        wsRef.current.close();
      }

      try {
        const queryParams = new URLSearchParams();
        if (apiKey) queryParams.set("apiKey", apiKey);
        if (selectedSerial && selectedSerial !== "ALL") queryParams.set("serial", selectedSerial);

        const url = `${serverUrl}?${queryParams.toString()}`;
        const ws = new WebSocket(url);
        wsRef.current = ws;

        ws.onopen = () => {
          if (!active) return;
          setIsConnected(true);
        };

        ws.onmessage = (event) => {
          if (!active) return;
          try {
            const data: WebSocketMessage = JSON.parse(event.data);

            if (data.type === "INIT_SNAPSHOT") {
              if (data.transactions) {
                setTransactions(data.transactions.reverse());
                if (data.transactions.length > 0) {
                  setSelectedTransactionId((prev) => prev || data.transactions![0].id);
                }
              }
              if (data.devices) {
                setDevices(data.devices);
              }
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
            } else if (data.type === "CLEAR") {
              setTransactions([]);
              setSelectedTransactionId(null);
              setSelectedField(null);
            }
          } catch (e) {
            console.error("Error parsing WebSocket message:", e);
          }
        };

        ws.onclose = () => {
          if (!active) return;
          setIsConnected(false);
          reconnectTimeoutRef.current = setTimeout(connectWebSocket, 3000);
        };

        ws.onerror = () => {
          if (!active) return;
          setIsConnected(false);
          ws.close();
        };
      } catch (e) {
        console.error("WebSocket connection failed:", e);
        setIsConnected(false);
        reconnectTimeoutRef.current = setTimeout(connectWebSocket, 4000);
      }
    };

    connectWebSocket();

    return () => {
      active = false;
      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      if (wsRef.current) wsRef.current.close();
    };
  }, [serverUrl, apiKey, selectedSerial]);

  // Selected Transaction Object
  const selectedTransaction = useMemo(() => {
    return transactions.find((t) => t.id === selectedTransactionId) || transactions[0] || null;
  }, [transactions, selectedTransactionId]);

  // Filtered transactions based on search query, device selector, and message filter
  const filteredTransactions = useMemo(() => {
    return transactions.filter((tx) => {
      // 1. Device filter
      if (selectedSerial && selectedSerial !== "ALL" && tx.serial !== selectedSerial) {
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
  }, [transactions, selectedSerial, messageFilter, searchQuery]);

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

    try {
      // 1. Send Request
      await fetch(httpEndpoint, {
        method: "POST",
        headers: {
          "Content-Type": "application/xml",
          "x-api-key": apiKey,
          "x-device-serial": randomSerial,
        },
        body: sampleRequestXml,
      });

      // 2. Wait 450ms and Send Response (simulating network roundtrip)
      setTimeout(async () => {
        await fetch(httpEndpoint, {
          method: "POST",
          headers: {
            "Content-Type": "application/xml",
            "x-api-key": apiKey,
            "x-device-serial": randomSerial,
          },
          body: sampleResponseXml,
        });
      }, 450);
    } catch (e) {
      console.error("Simulation error:", e);
    }
  };

  // Handler: Clear session
  const handleClear = async () => {
    try {
      const clearUrl = httpEndpoint.replace("/events", "/clear");
      await fetch(clearUrl, {
        method: "POST",
        headers: { "x-api-key": apiKey },
      });
      setTransactions([]);
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
    <div className="flex flex-col h-screen overflow-hidden bg-[#090D16] text-slate-100 font-sans">
      {/* 1. Fixed Top Toolbar */}
      <TopBar
        isConnected={isConnected}
        selectedSerial={selectedSerial}
        onSelectSerial={setSelectedSerial}
        devices={devices}
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        messageFilter={messageFilter}
        onMessageFilterChange={setMessageFilter}
        onSimulate={handleSimulate}
        onClear={handleClear}
        onOpenSettings={() => setIsSettingsOpen(true)}
        onExport={handleExport}
        packetCount={filteredTransactions.length}
      />

      {/* 2. Main 3-Panel Content Area */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left Panel: Transaction Timeline (28% width) */}
        <div className="w-80 md:w-96 flex-shrink-0 h-full">
          <TransactionTimeline
            transactions={filteredTransactions}
            selectedTransactionId={selectedTransaction?.id || null}
            onSelectTransaction={(tx) => {
              setSelectedTransactionId(tx.id);
              setSelectedField(null);
            }}
          />
        </div>

        {/* Center Panel: Packet Inspector (Flex 1) */}
        <PacketInspector
          transaction={selectedTransaction}
          selectedField={selectedField}
          onSelectField={(field) => setSelectedField(field)}
        />

        {/* Right Panel: Field Detail Deep-Dive (w-80 / w-96) */}
        <FieldDetailPanel field={selectedField} />
      </div>

      {/* 3. Settings Modal */}
      <SettingsModal
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        apiKey={apiKey}
        onSaveApiKey={(key) => {
          setApiKey(key);
          if (typeof window !== "undefined") localStorage.setItem("tap8583_api_key", key);
        }}
        serverUrl={serverUrl}
        onSaveServerUrl={(url) => {
          setServerUrl(url);
          const httpUrl = url
            .replace("ws://", "http://")
            .replace("wss://", "https://")
            .replace("/ws/stream", "/api/v1/events");
          setHttpEndpoint(httpUrl);
          if (typeof window !== "undefined") localStorage.setItem("tap8583_server_url", url);
        }}
      />
    </div>
  );
}

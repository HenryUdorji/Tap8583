"use client";

import React, { useEffect, useState } from "react";
import { CheckCircle2, KeyRound, Loader2, Radio } from "lucide-react";
import { ApiError, IngestKey, LISTENER_VERSION, Workspace, request, streamAuthFrame } from "@/lib/api";
import { apiUrl, streamUrl } from "@/lib/connection";
import { CodeBlock, CopyButton } from "./CopyButton";

export function installSnippets(serverUrl: string, key: string) {
  return {
    gradle: `debugImplementation("dev.sarari:tap8583-listener:${LISTENER_VERSION}")`,
    kotlin: `// In Application.onCreate(), debug builds only
VisualizerLogListener.install(
    apiKey = "${key}",
    endpoint = "${serverUrl}/api/v1/events"
)`,
  };
}

const testEvent = (serial: string, stan: string, at: string, response: boolean) => `<visualizer-event realm="channel/test" at="${at}" lifespan="1ms" tag="${response ? "receive" : "send"}" serial="${serial}">
  <isomsg direction="${response ? "incoming" : "outgoing"}">
    <field id="0" value="${response ? "0810" : "0800"}"/>
    <field id="7" value="1010120000"/>
    <field id="11" value="${stan}"/>
    <field id="70" value="301"/>${response ? `\n    <field id="39" value="00"/>` : ""}
  </isomsg>
</visualizer-event>`;

/** Sends an echo test (0800/0810) with the terminal key, exactly as a terminal would. */
async function sendTestTransaction(serverUrl: string, key: string) {
  const stan = String(Math.floor(100000 + Math.random() * 900000));
  const now = Date.now();
  for (const [offset, response] of [[0, false], [180, true]] as const) {
    const res = await fetch(apiUrl(serverUrl, "/events"), {
      method: "POST",
      headers: { "Content-Type": "application/xml", "x-api-key": key },
      body: testEvent("TEST-TERMINAL", stan, new Date(now + offset).toISOString(), response),
    });
    if (!res.ok) throw new ApiError(res.status, `The server rejected the key (HTTP ${res.status})`);
  }
}

interface ConnectTerminalProps {
  serverUrl: string;
  workspace: Workspace;
  /** Called with the device serial once the first event arrives. */
  onConnected?: (serial: string) => void;
  /** Listen for the first event from this workspace. */
  waitForFirstEvent?: boolean;
  /** Called after a key is created, so lists can refresh. */
  onKeyCreated?: (key: IngestKey) => void;
}

/**
 * Create a terminal key, copy the install snippet, then watch for the first
 * event from that workspace.
 */
export const ConnectTerminal: React.FC<ConnectTerminalProps> = ({
  serverUrl,
  workspace,
  onConnected,
  waitForFirstEvent = true,
  onKeyCreated,
}) => {
  const [name, setName] = useState("Debug build");
  const [created, setCreated] = useState<{ key: IngestKey; value: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [connectedSerial, setConnectedSerial] = useState<string | null>(null);
  const isOwner = workspace.role === "owner";

  // Watch the workspace's stream for the first transaction or channel event.
  useEffect(() => {
    if (!waitForFirstEvent || !created) return;
    let closed = false;
    const ws = new WebSocket(streamUrl(serverUrl));
    ws.onopen = () => ws.send(streamAuthFrame({ mode: "accounts", workspaceId: workspace.id }, "ALL"));
    ws.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data);
        const serial = msg.transaction?.serial || msg.channelEvent?.serial;
        if ((msg.type === "TRANSACTION_UPDATED" || msg.type === "CHANNEL_EVENT") && !closed) {
          setConnectedSerial(serial || "your terminal");
          onConnected?.(serial || "");
        }
      } catch {
        // ignore
      }
    };
    return () => {
      closed = true;
      ws.close();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [created, serverUrl, workspace.id, waitForFirstEvent]);

  const createKey = async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await request<{ key: IngestKey; value: string }>(serverUrl, `/workspaces/${workspace.id}/keys`, {
        method: "POST",
        body: JSON.stringify({ name }),
      });
      setCreated(result);
      onKeyCreated?.(result.key);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not create the key");
    } finally {
      setBusy(false);
    }
  };

  const test = async () => {
    if (!created) return;
    setError(null);
    try {
      await sendTestTransaction(serverUrl, created.value);
    } catch (e) {
      setError(e instanceof Error ? e.message : "The test transaction failed");
    }
  };

  if (!isOwner) {
    return (
      <p className="text-xs text-slate-400">
        Only workspace owners can create terminal keys. Ask an owner of {workspace.name} for one, then add the listener
        to your debug build.
      </p>
    );
  }

  if (!created) {
    return (
      <div className="space-y-3">
        <p className="text-xs text-slate-400">
          A terminal key lets a POS app send its ISO 8583 traffic to this workspace. Create one per app or team so you can
          revoke it on its own.
        </p>
        <label className="block text-xs font-medium text-slate-300">
          Key name
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={80}
            className="mt-1 w-full bg-slate-900/80 border border-white/10 focus:border-sky-500/50 rounded-lg px-3 py-2 text-sm text-slate-100 outline-none"
          />
        </label>
        {error && <p role="alert" className="text-xs text-rose-400">{error}</p>}
        <button
          type="button"
          onClick={createKey}
          disabled={busy || !name.trim()}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-sky-500 hover:bg-sky-400 disabled:opacity-60 text-on-accent text-xs font-semibold"
        >
          <KeyRound className="w-3.5 h-3.5" />
          {busy ? "Creating…" : "Create terminal key"}
        </button>
      </div>
    );
  }

  const snippets = installSnippets(serverUrl, created.value);
  return (
    <div className="space-y-3">
      <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 space-y-2">
        <div className="text-xs text-slate-200">
          <span className="font-semibold text-amber-400">Copy this key now.</span> It is shown only once. If you lose it,
          create a new one and revoke this.
        </div>
        <div className="flex items-center gap-2">
          <code className="flex-1 min-w-0 truncate font-mono text-xs text-slate-100 bg-code border border-white/10 rounded px-2 py-1.5">
            {created.value}
          </code>
          <CopyButton text={created.value} />
        </div>
      </div>
      <CodeBlock label="1. Add the listener (app/build.gradle.kts)" code={snippets.gradle} />
      <CodeBlock label="2. Start it in your debug build" code={snippets.kotlin} />

      {waitForFirstEvent &&
        (connectedSerial ? (
          <div className="flex items-center gap-2 rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-3 py-2 text-xs text-emerald-400 font-medium">
            <CheckCircle2 className="w-4 h-4" />
            {connectedSerial} is connected. Its transactions will appear on the dashboard.
          </div>
        ) : (
          <div className="flex flex-wrap items-center gap-2 rounded-lg border border-white/10 bg-slate-900/60 px-3 py-2 text-xs text-slate-300">
            <Loader2 className="w-3.5 h-3.5 animate-spin text-sky-400" />
            <span className="flex-1 min-w-0">Waiting for your first transaction…</span>
            <button
              type="button"
              onClick={test}
              className="inline-flex items-center gap-1 px-2 py-1 rounded-md border border-white/10 text-[11px] font-medium text-slate-300 hover:text-slate-100"
            >
              <Radio className="w-3 h-3" />
              Send a test transaction
            </button>
          </div>
        ))}
      {error && <p role="alert" className="text-xs text-rose-400">{error}</p>}
    </div>
  );
};

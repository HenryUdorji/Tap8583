"use client";

import React, { useEffect, useState } from "react";
import { Key, Server, Settings, X } from "lucide-react";

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  apiKey: string;
  /** The user's explicit server URL, or "" to use the default. */
  serverUrlOverride: string;
  /** Shown as the placeholder when no override is set. */
  defaultServerUrl: string;
  onSave: (apiKey: string, serverUrlOverride: string) => void;
}

export const SettingsModal: React.FC<SettingsModalProps> = ({
  isOpen,
  onClose,
  apiKey,
  serverUrlOverride,
  defaultServerUrl,
  onSave,
}) => {
  const [localKey, setLocalKey] = useState(apiKey);
  const [localUrl, setLocalUrl] = useState(serverUrlOverride);

  // Start from the current values each time the dialog opens.
  useEffect(() => {
    if (isOpen) {
      setLocalKey(apiKey);
      setLocalUrl(serverUrlOverride);
    }
  }, [isOpen, apiKey, serverUrlOverride]);

  if (!isOpen) return null;

  const handleSave = () => {
    onSave(localKey, localUrl);
    // Close straight away: if the key is rejected the dashboard reopens this dialog.
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4 select-none">
      <div className="bg-raised border border-white/10 rounded-2xl w-full max-w-md overflow-hidden shadow-2xl animate-in fade-in zoom-in-95 duration-200">
        {/* Header */}
        <div className="px-5 py-4 border-b border-white/10 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Settings className="w-4 h-4 text-sky-400" />
            <span className="font-bold text-sm text-slate-100">Tap8583 Configuration</span>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Form Body */}
        <div className="p-5 space-y-4">
          {/* Team API Key */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
              <Key className="w-3.5 h-3.5 text-sky-400" />
              <span>Viewer API Key</span>
            </label>
            <input
              type="password"
              autoComplete="off"
              value={localKey}
              onChange={(e) => setLocalKey(e.target.value)}
              placeholder="Paste your viewer key"
              className="w-full bg-slate-900 border border-white/10 focus:border-sky-500/60 rounded-lg px-3 py-2 text-xs font-mono text-slate-200 outline-none transition-all"
            />
            <p className="text-[11px] text-slate-500">
              Needed to see transactions. It is stored only in this browser.
            </p>
          </div>

          {/* Server Ingestion & WebSocket URL */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
              <Server className="w-3.5 h-3.5 text-indigo-400" />
              <span>Server URL (optional)</span>
            </label>
            <input
              type="text"
              value={localUrl}
              onChange={(e) => setLocalUrl(e.target.value)}
              placeholder={defaultServerUrl || "https://tap8583.example.com"}
              className="w-full bg-slate-900 border border-white/10 focus:border-indigo-500/60 rounded-lg px-3 py-2 text-xs font-mono text-slate-200 outline-none transition-all"
            />
            <p className="text-[11px] text-slate-500">
              Leave blank to use the server this dashboard is hosted on.
            </p>
          </div>

          {/* Android Library Quick Setup Info */}
          <div className="bg-slate-900/80 border border-white/5 rounded-xl p-3 text-xs font-mono space-y-1">
            <div className="text-slate-400 font-sans font-semibold text-[11px]">
              Android Quick Start (listenerLib):
            </div>
            <div className="text-slate-300 text-[11px] bg-black/40 p-2 rounded select-text">
              VisualizerLogListener.install(apiKey = &quot;YOUR_INGEST_KEY&quot;, endpoint = &quot;{(localUrl || defaultServerUrl || "https://your-server")}/api/v1/events&quot;)
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="px-5 py-3 bg-slate-900/60 border-t border-white/10 flex items-center justify-end gap-2">
          <button
            onClick={onClose}
            className="px-3 py-1.5 rounded-lg text-xs font-medium text-slate-400 hover:text-slate-200 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            className="flex items-center gap-1.5 px-4 py-1.5 rounded-lg bg-sky-500 hover:bg-sky-400 text-slate-950 font-semibold text-xs shadow-lg shadow-sky-500/20 transition-all"
          >
            <span>Save Changes</span>
          </button>
        </div>
      </div>
    </div>
  );
};

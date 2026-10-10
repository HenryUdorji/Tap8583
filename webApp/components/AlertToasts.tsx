"use client";

import React, { useEffect, useRef } from "react";
import { AlertTriangle, X, XCircle } from "lucide-react";
import { Alert } from "@/lib/alerts";

const AUTO_DISMISS_MS = 8_000;

interface AlertToastsProps {
  alerts: Alert[];
  onDismiss: (id: string) => void;
  onOpen: (alert: Alert) => void;
}

const Toast: React.FC<{ alert: Alert; onDismiss: () => void; onOpen: () => void }> = ({ alert, onDismiss, onOpen }) => {
  // The parent re-renders on every live update; keep one timer per toast.
  const dismissRef = useRef(onDismiss);
  dismissRef.current = onDismiss;
  useEffect(() => {
    const id = setTimeout(() => dismissRef.current(), AUTO_DISMISS_MS);
    return () => clearTimeout(id);
  }, []);

  const isError = alert.level === "error";
  const Icon = isError ? XCircle : AlertTriangle;

  return (
    <div
      role="status"
      className={`pointer-events-auto flex items-start gap-2 w-full rounded-xl border px-3 py-2.5 shadow-2xl bg-raised ${
        isError ? "border-rose-500/40" : "border-amber-500/40"
      }`}
    >
      <Icon className={`w-4 h-4 mt-0.5 flex-shrink-0 ${isError ? "text-rose-400" : "text-amber-400"}`} />
      <button type="button" onClick={onOpen} disabled={!alert.transactionId} className="flex-1 min-w-0 text-left">
        <div className="text-xs font-semibold text-slate-100">{alert.title}</div>
        <div className="text-[11px] text-slate-400 break-words">{alert.detail}</div>
      </button>
      <button
        type="button"
        onClick={onDismiss}
        aria-label="Dismiss alert"
        className="p-0.5 rounded text-slate-500 hover:text-slate-200"
      >
        <X className="w-3.5 h-3.5" />
      </button>
    </div>
  );
};

/** Stack of pop-up alerts in the bottom-right corner, newest at the bottom. */
export const AlertToasts: React.FC<AlertToastsProps> = ({ alerts, onDismiss, onOpen }) => {
  if (alerts.length === 0) return null;
  return (
    <div className="fixed bottom-4 right-4 left-4 sm:left-auto z-50 flex flex-col gap-2 sm:w-80 pointer-events-none" aria-live="polite">
      {alerts.map((a) => (
        <Toast key={a.id} alert={a} onDismiss={() => onDismiss(a.id)} onOpen={() => onOpen(a)} />
      ))}
    </div>
  );
};

"use client";

import React, { useState } from "react";
import { Check, Copy } from "lucide-react";

/** Copies [text] and confirms for a moment. */
export const CopyButton: React.FC<{ text: string; label?: string; className?: string }> = ({ text, label = "Copy", className = "" }) => {
  const [copied, setCopied] = useState(false);
  return (
    <button
      type="button"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(text);
          setCopied(true);
          setTimeout(() => setCopied(false), 1500);
        } catch {
          // Clipboard blocked: the text is still selectable on screen.
        }
      }}
      className={`inline-flex items-center gap-1 px-2 py-1 rounded-md border border-white/10 bg-slate-800/80 text-[11px] font-medium text-slate-300 hover:text-slate-100 ${className}`}
    >
      {copied ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
      {copied ? "Copied" : label}
    </button>
  );
};

/** A block of code with a copy button. */
export const CodeBlock: React.FC<{ code: string; label?: string }> = ({ code, label }) => (
  <div className="rounded-lg border border-white/10 bg-code overflow-hidden">
    <div className="flex items-center justify-between gap-2 px-3 py-1.5 border-b border-white/10">
      <span className="text-[11px] text-slate-400">{label}</span>
      <CopyButton text={code} />
    </div>
    <pre className="p-3 text-[11px] leading-relaxed font-mono text-slate-200 overflow-x-auto whitespace-pre">{code}</pre>
  </div>
);

"use client";

import React, { useEffect, useRef, useState } from "react";
import { FileText, Upload, X } from "lucide-react";

/** Matches the server's limit on POST /api/v1/decode. */
const MAX_LOG_BYTES = 2 * 1024 * 1024;

interface DecodeLogModalProps {
  isOpen: boolean;
  onClose: () => void;
  /** Sends the text to the server; resolves to an error message or null on success. */
  onDecode: (text: string, sourceName: string) => Promise<string | null>;
}

const PLACEHOLDER = `<log realm="channel/196.6.103.18:4018" at="2026-05-21T14:20:58.266" lifespan="19ms">
  <send>
    <isomsg direction="outgoing">
      <field id="0" value="0200"/>
      <field id="11" value="086503"/>
      …
    </isomsg>
  </send>
</log>`;

/** Paste or upload a jPOS log and decode it without a terminal. */
export const DecodeLogModal: React.FC<DecodeLogModalProps> = ({ isOpen, onClose, onDecode }) => {
  const [text, setText] = useState("");
  const [sourceName, setSourceName] = useState("Pasted log");
  const [error, setError] = useState<string | null>(null);
  const [isDecoding, setIsDecoding] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) setError(null);
  }, [isOpen]);

  if (!isOpen) return null;

  const handleFile = async (file: File | undefined) => {
    if (!file) return;
    if (file.size > MAX_LOG_BYTES) {
      setError("That file is larger than 2 MB. Trim it to the part around the transactions you need.");
      return;
    }
    setText(await file.text());
    setSourceName(file.name);
    setError(null);
  };

  const handleDecode = async () => {
    if (!text.trim()) {
      setError("Paste a log or choose a file first.");
      return;
    }
    if (new Blob([text]).size > MAX_LOG_BYTES) {
      setError("That log is larger than 2 MB. Trim it to the part around the transactions you need.");
      return;
    }
    setIsDecoding(true);
    const problem = await onDecode(text, sourceName);
    setIsDecoding(false);
    if (problem) setError(problem);
    else onClose();
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-label="Decode a jPOS log">
      <div className="bg-raised border border-white/10 rounded-2xl w-full max-w-2xl max-h-full flex flex-col overflow-hidden shadow-2xl">
        <div className="px-5 py-4 border-b border-white/10 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <FileText className="w-4 h-4 text-sky-400" />
            <span className="font-bold text-sm text-slate-100">Decode a jPOS log</span>
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            className="p-1 rounded-lg hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-5 space-y-3 overflow-y-auto">
          <p className="text-xs text-slate-400">
            Paste jPOS logger output, Logcat lines or Tap8583 listener events. Requests and responses are paired by STAN,
            and timeouts and disconnects in the log are shown too. The log is decoded on the server, masked like live
            traffic, and not stored or shown to anyone else.
          </p>
          <textarea
            value={text}
            onChange={(e) => {
              setText(e.target.value);
              setSourceName("Pasted log");
            }}
            placeholder={PLACEHOLDER}
            spellCheck={false}
            className="w-full h-64 bg-code border border-white/10 focus:border-sky-500/50 rounded-lg p-3 text-xs font-mono text-slate-200 placeholder-slate-600 outline-none resize-y"
          />
          <div className="flex flex-wrap items-center gap-2">
            <input
              ref={fileRef}
              type="file"
              accept=".log,.txt,.xml,text/plain,application/xml"
              className="hidden"
              onChange={(e) => {
                handleFile(e.target.files?.[0]);
                e.target.value = "";
              }}
            />
            <button
              type="button"
              onClick={() => fileRef.current?.click()}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-white/10 bg-slate-800/80 text-xs font-medium text-slate-300 hover:text-slate-100"
            >
              <Upload className="w-3.5 h-3.5" />
              Choose a file
            </button>
            <span className="text-[11px] text-slate-500">.log, .txt or .xml, up to 2 MB</span>
          </div>
          {error && (
            <div role="alert" className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-xs text-rose-400">
              {error}
            </div>
          )}
        </div>

        <div className="px-5 py-3 border-t border-white/10 flex justify-end gap-2">
          <button
            onClick={onClose}
            className="px-3 py-1.5 rounded-lg text-xs font-medium text-slate-400 hover:text-slate-200"
          >
            Cancel
          </button>
          <button
            onClick={handleDecode}
            disabled={isDecoding}
            className="px-4 py-1.5 rounded-lg bg-sky-500 hover:bg-sky-400 disabled:opacity-60 text-on-accent text-xs font-semibold"
          >
            {isDecoding ? "Decoding…" : "Decode"}
          </button>
        </div>
      </div>
    </div>
  );
};

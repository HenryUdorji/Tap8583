"use client";

import React, { useState } from "react";
import { Check, Copy } from "lucide-react";

interface RawXmlViewerProps {
  xml: string;
}

export const RawXmlViewer: React.FC<RawXmlViewerProps> = ({ xml }) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    if (!xml) return;
    navigator.clipboard.writeText(xml);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (!xml) {
    return (
      <div className="p-8 text-center text-xs text-slate-500 font-mono italic">
        No raw XML available for this packet.
      </div>
    );
  }

  // Syntax highlighting for XML tags, attributes, and values.
  // Rendered as React elements (never as HTML) because packet XML comes from
  // terminals over the network and must not be able to inject markup.
  const renderHighlightedXml = (raw: string) => {
    const lines = raw.split("\n");
    return lines.map((line, idx) => (
      <div key={idx} className="flex">
        <span className="w-8 select-none text-right pr-3 text-slate-600 font-mono text-[11px]">
          {idx + 1}
        </span>
        <span className="flex-1 font-mono text-xs text-slate-200 whitespace-pre-wrap break-all">
          {highlightXmlLine(line)}
        </span>
      </div>
    ));
  };

  return (
    <div className="flex flex-col h-full bg-[#080D1A] overflow-hidden relative select-text">
      {/* Top Bar with Copy controls */}
      <div className="flex items-center justify-between px-4 py-2 bg-slate-900/80 border-b border-white/10 text-xs font-mono">
        <span className="text-slate-400">jPOS Visualizer XML Stream</span>
        <button
          onClick={handleCopy}
          className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 border border-white/10 transition-colors text-xs font-medium"
        >
          {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
          <span>{copied ? "Copied" : "Copy XML"}</span>
        </button>
      </div>

      {/* Code Container */}
      <div className="flex-1 overflow-auto p-4 font-mono space-y-0.5">
        {renderHighlightedXml(xml)}
      </div>
    </div>
  );
};

// Comments, tag openings (<name, </name), attributes (name="value"), and tag endings (>, />).
const XML_TOKEN = /(<!--.*?-->)|(<\/?[\w:.-]+)|([\w:.-]+)(=)("[^"]*"|'[^']*')|(\/?>)/g;

function highlightXmlLine(line: string): React.ReactNode[] {
  const nodes: React.ReactNode[] = [];
  let last = 0;
  let key = 0;
  for (const match of line.matchAll(XML_TOKEN)) {
    const start = match.index ?? 0;
    if (start > last) nodes.push(line.slice(last, start));
    if (match[1]) {
      nodes.push(<span key={key++} className="text-slate-500 italic">{match[1]}</span>);
    } else if (match[2]) {
      nodes.push(<span key={key++} className="text-sky-400">{match[2]}</span>);
    } else if (match[3]) {
      nodes.push(<span key={key++} className="text-purple-400">{match[3]}</span>);
      nodes.push(match[4]);
      nodes.push(<span key={key++} className="text-emerald-300">{match[5]}</span>);
    } else if (match[6]) {
      nodes.push(<span key={key++} className="text-sky-400">{match[6]}</span>);
    }
    last = start + match[0].length;
  }
  if (last < line.length) nodes.push(line.slice(last));
  return nodes;
}

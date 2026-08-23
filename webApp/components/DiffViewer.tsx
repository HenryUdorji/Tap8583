"use client";

import React from "react";
import { ArrowRight, Check, Sparkles } from "lucide-react";
import { DecodedPacketDto } from "@/lib/types";

interface DiffViewerProps {
  request: DecodedPacketDto | null;
  response: DecodedPacketDto | null;
}

export const DiffViewer: React.FC<DiffViewerProps> = ({ request, response }) => {
  if (!request && !response) {
    return (
      <div className="p-8 text-center text-xs text-slate-500 font-mono">
        Select a transaction to inspect request and response diff.
      </div>
    );
  }

  // Collect all unique field numbers across request and response
  const reqFieldsMap = new Map(request?.fields.map((f) => [f.number, f]));
  const respFieldsMap = new Map(response?.fields.map((f) => [f.number, f]));

  const allFieldNumbers = Array.from(
    new Set([...Array.from(reqFieldsMap.keys()), ...Array.from(respFieldsMap.keys())])
  ).sort((a, b) => a - b);

  return (
    <div className="flex flex-col h-full overflow-hidden select-text">
      {/* Diff Table Header */}
      <div className="grid grid-cols-2 bg-slate-900/90 border-b border-white/10 text-xs font-mono font-semibold py-2.5 px-4 sticky top-0 z-10">
        <div className="flex items-center gap-2 text-sky-400">
          <span className="w-2 h-2 rounded-full bg-sky-400" />
          <span>REQUEST ({request?.mti.raw || "N/A"})</span>
        </div>
        <div className="flex items-center gap-2 text-emerald-400">
          <span className="w-2 h-2 rounded-full bg-emerald-400" />
          <span>RESPONSE ({response?.mti.raw || "Pending"})</span>
        </div>
      </div>

      {/* Diff Rows */}
      <div className="flex-1 overflow-y-auto divide-y divide-white/5 font-mono text-xs">
        {allFieldNumbers.map((num) => {
          const reqField = reqFieldsMap.get(num);
          const respField = respFieldsMap.get(num);

          const isOnlyInReq = reqField && !respField;
          const isOnlyInResp = !reqField && respField;
          const isModified = reqField && respField && reqField.rawValue !== respField.rawValue;
          const isSame = reqField && respField && reqField.rawValue === respField.rawValue;

          const fieldName = reqField?.name || respField?.name || `Field ${num}`;

          return (
            <div
              key={num}
              className={`grid grid-cols-2 transition-colors ${
                isOnlyInResp
                  ? "bg-emerald-500/5 hover:bg-emerald-500/10"
                  : isModified
                  ? "bg-amber-500/5 hover:bg-amber-500/10"
                  : "hover:bg-slate-800/40"
              }`}
            >
              {/* Left Side (Request) */}
              <div className="p-3 border-r border-white/10 flex flex-col justify-center">
                {reqField ? (
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span className="px-1.5 py-0.2 rounded bg-white/10 text-slate-300 font-bold text-[10px]">
                        DE {num}
                      </span>
                      <span className="text-[11px] font-medium text-slate-300 truncate">
                        {fieldName}
                      </span>
                      {isOnlyInReq && (
                        <span className="text-[9px] px-1 py-0.2 rounded bg-sky-500/20 text-sky-300 font-sans">
                          Req only
                        </span>
                      )}
                    </div>
                    <div className="text-slate-100 text-xs break-all bg-black/30 p-1.5 rounded border border-white/5">
                      {reqField.decodedValue || reqField.rawValue}
                    </div>
                  </div>
                ) : (
                  <div className="text-slate-600 italic text-[11px]">— (Not present in request)</div>
                )}
              </div>

              {/* Right Side (Response) */}
              <div className="p-3 flex flex-col justify-center">
                {respField ? (
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span className="px-1.5 py-0.2 rounded bg-white/10 text-slate-300 font-bold text-[10px]">
                        DE {num}
                      </span>
                      <span className="text-[11px] font-medium text-slate-300 truncate">
                        {fieldName}
                      </span>
                      {isOnlyInResp && (
                        <span className="text-[9px] px-1.5 py-0.5 rounded bg-emerald-500/20 border border-emerald-500/30 text-emerald-300 font-sans font-semibold flex items-center gap-1">
                          <Sparkles className="w-2.5 h-2.5" />
                          New in response
                        </span>
                      )}
                      {isModified && (
                        <span className="text-[9px] px-1.5 py-0.5 rounded bg-amber-500/20 border border-amber-500/30 text-amber-300 font-sans font-semibold">
                          Modified
                        </span>
                      )}
                      {isSame && (
                        <span className="text-[9px] text-slate-500 flex items-center gap-0.5">
                          <Check className="w-2.5 h-2.5" />
                          Matched
                        </span>
                      )}
                    </div>
                    <div
                      className={`text-xs break-all p-1.5 rounded border ${
                        isOnlyInResp
                          ? "bg-emerald-500/10 border-emerald-500/30 text-emerald-200"
                          : isModified
                          ? "bg-amber-500/10 border-amber-500/30 text-amber-200"
                          : "bg-black/30 border-white/5 text-slate-100"
                      }`}
                    >
                      {respField.decodedValue || respField.rawValue}
                    </div>
                  </div>
                ) : (
                  <div className="text-slate-600 italic text-[11px]">— (Not present in response)</div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

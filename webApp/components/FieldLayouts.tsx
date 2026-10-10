"use client";

import React, { useCallback, useEffect, useState } from "react";
import { Pencil, Plus, Trash2, X } from "lucide-react";
import { FieldLayout, FieldPreview, LayoutPart, Workspace, request } from "@/lib/api";

/** Card and chip data keep their built-in decoding (the server enforces this too). */
export const PROTECTED_FIELDS = new Set([0, 1, 2, 14, 34, 35, 36, 45, 52, 55]);

const inputClass =
  "w-full bg-slate-900/80 border border-white/10 focus:border-sky-500/50 rounded-lg px-3 py-2 text-sm text-slate-100 placeholder-slate-500 outline-none";
const smallButton =
  "inline-flex items-center gap-1 px-2 py-1 rounded-md border border-white/10 text-[11px] font-medium text-slate-300 hover:text-slate-100 disabled:opacity-50";

const FORMATS: { id: FieldLayout["format"]; label: string; hint: string }[] = [
  { id: "tlv", label: "Tags", hint: "Tag, then a length, then the value, repeated (e.g. 01004ABCD)" },
  { id: "fixed", label: "Fixed positions", hint: "Parts of known length, one after another" },
  { id: "text", label: "Name only", hint: "Just give the field a name and a description" },
];

export function describeLayout(layout: FieldLayout): string {
  if (layout.format === "tlv") {
    return `Tags: ${layout.tagLength}-character tag, ${layout.lengthDigits}-digit length · ${layout.parts.length} named`;
  }
  if (layout.format === "fixed") return `Fixed positions · ${layout.parts.length} part${layout.parts.length === 1 ? "" : "s"}`;
  return "Name only";
}

const blank = (field?: number | null): FieldLayout => ({
  field: field ?? 62,
  name: "",
  description: "",
  format: "tlv",
  tagLength: 2,
  lengthDigits: 3,
  parts: [],
});

interface FieldLayoutsTabProps {
  serverUrl: string;
  workspace: Workspace;
  /** Opens the editor for this field straight away. */
  initialField?: number | null;
}

/** The workspace's private-field layouts, and the editor for one. */
export const FieldLayoutsTab: React.FC<FieldLayoutsTabProps> = ({ serverUrl, workspace, initialField }) => {
  const [layouts, setLayouts] = useState<FieldLayout[] | null>(null);
  const [editing, setEditing] = useState<{ layout: FieldLayout; isNew: boolean } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const list = await request<FieldLayout[]>(serverUrl, `/workspaces/${workspace.id}/fields`);
      setLayouts(list);
      return list;
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load field layouts");
      return null;
    }
  }, [serverUrl, workspace.id]);

  useEffect(() => {
    setEditing(null);
    load().then((list) => {
      if (initialField == null || !list) return;
      const existing = list.find((l) => l.field === initialField);
      setEditing(existing ? { layout: existing, isNew: false } : { layout: blank(initialField), isNew: true });
    });
  }, [load, initialField]);

  if (editing) {
    return (
      <FieldLayoutEditor
        serverUrl={serverUrl}
        workspace={workspace}
        initial={editing.layout}
        isNew={editing.isNew}
        takenFields={(layouts || []).map((l) => l.field)}
        onDone={async () => {
          setEditing(null);
          await load();
        }}
      />
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs text-slate-400">
          Tell Tap8583 how your acquirer lays out its private fields, like DE48 or DE62. Everyone in {workspace.name} sees
          them explained, including transactions already stored.
        </p>
        <button type="button" onClick={() => setEditing({ layout: blank(), isNew: true })} className={`${smallButton} flex-shrink-0`}>
          <Plus className="w-3 h-3" />
          Describe a field
        </button>
      </div>
      {error && <p role="alert" className="text-xs text-rose-400">{error}</p>}
      {layouts === null ? (
        <p className="text-xs text-slate-500">Loading…</p>
      ) : layouts.length === 0 ? (
        <p className="text-xs text-slate-400">
          No fields described yet. You can also start from any field in a transaction: open it and choose &quot;Describe this
          field&quot;.
        </p>
      ) : (
        <ul className="divide-y divide-white/5 rounded-lg border border-white/10">
          {layouts.map((l) => (
            <li key={l.field} className="flex items-center gap-3 px-3 py-2.5">
              <span className="px-1.5 py-0.5 rounded bg-white/10 text-slate-100 font-mono font-bold text-[11px] flex-shrink-0">
                DE {l.field}
              </span>
              <div className="min-w-0 flex-1">
                <div className="text-sm font-medium text-slate-100 truncate">{l.name}</div>
                <div className="text-[11px] text-slate-400 truncate">{describeLayout(l)}</div>
              </div>
              <button type="button" onClick={() => setEditing({ layout: l, isNew: false })} className={smallButton}>
                <Pencil className="w-3 h-3" />
                Edit
              </button>
              <button
                type="button"
                aria-label={`Remove the layout for DE ${l.field}`}
                onClick={async () => {
                  setError(null);
                  try {
                    await request(serverUrl, `/workspaces/${workspace.id}/fields/${l.field}`, { method: "DELETE" });
                    await load();
                  } catch (e) {
                    setError(e instanceof Error ? e.message : "Could not remove it");
                  }
                }}
                className={smallButton}
              >
                <Trash2 className="w-3 h-3" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

interface FieldLayoutEditorProps {
  serverUrl: string;
  workspace: Workspace;
  initial: FieldLayout;
  isNew: boolean;
  takenFields: number[];
  onDone: () => void;
}

const FieldLayoutEditor: React.FC<FieldLayoutEditorProps> = ({ serverUrl, workspace, initial, isNew, takenFields, onDone }) => {
  const [layout, setLayout] = useState<FieldLayout>({ ...initial, description: initial.description || "" });
  const [sample, setSample] = useState("");
  const [preview, setPreview] = useState<FieldPreview | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const set = (patch: Partial<FieldLayout>) => setLayout((l) => ({ ...l, ...patch }));
  const setPart = (i: number, patch: Partial<LayoutPart>) =>
    setLayout((l) => ({ ...l, parts: l.parts.map((p, j) => (j === i ? { ...p, ...patch } : p)) }));

  const fieldProblem =
    !Number.isInteger(layout.field) || layout.field < 2 || layout.field > 128
      ? "Pick a field between 2 and 128"
      : PROTECTED_FIELDS.has(layout.field)
      ? `DE ${layout.field} holds card or chip data and keeps its built-in decoding`
      : isNew && takenFields.includes(layout.field)
      ? `DE ${layout.field} is already described; saving replaces it`
      : null;

  // The latest value this workspace has seen for the field, to try the layout on.
  useEffect(() => {
    if (!Number.isInteger(layout.field) || PROTECTED_FIELDS.has(layout.field) || layout.field < 2 || layout.field > 128) return;
    let active = true;
    request<{ raw?: string | null }>(serverUrl, `/workspaces/${workspace.id}/fields/${layout.field}/sample`)
      .then((s) => active && s.raw && setSample((current) => current || s.raw!))
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [serverUrl, workspace.id, layout.field]);

  // Live preview against the sample.
  useEffect(() => {
    if (!sample || layout.format === "text" || !layout.name.trim()) {
      setPreview(null);
      setPreviewError(null);
      return;
    }
    const timer = setTimeout(async () => {
      try {
        setPreview(
          await request<FieldPreview>(serverUrl, `/workspaces/${workspace.id}/fields/preview`, {
            method: "POST",
            body: JSON.stringify({ layout, raw: sample }),
          })
        );
        setPreviewError(null);
      } catch (e) {
        setPreview(null);
        setPreviewError(e instanceof Error ? e.message : "Could not preview");
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [serverUrl, workspace.id, layout, sample]);

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await request(serverUrl, `/workspaces/${workspace.id}/fields/${layout.field}`, {
        method: "PUT",
        body: JSON.stringify(layout),
      });
      onDone();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save");
      setBusy(false);
    }
  };

  return (
    <form onSubmit={save} className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-100">{isNew ? "Describe a field" : `DE ${layout.field}`}</h3>
        <button type="button" onClick={onDone} aria-label="Back to fields" className="p-1 rounded-lg hover:bg-slate-800 text-slate-400">
          <X className="w-4 h-4" />
        </button>
      </div>

      <div className="grid grid-cols-[6rem_1fr] gap-3">
        <label className="block text-xs font-medium text-slate-300">
          Field
          <input
            type="number"
            min={2}
            max={128}
            disabled={!isNew}
            value={Number.isNaN(layout.field) ? "" : layout.field}
            onChange={(e) => {
              set({ field: parseInt(e.target.value, 10) });
              setSample("");
            }}
            className={`${inputClass} mt-1 font-mono disabled:opacity-60`}
          />
        </label>
        <label className="block text-xs font-medium text-slate-300">
          Name
          <input
            required
            maxLength={80}
            value={layout.name}
            onChange={(e) => set({ name: e.target.value })}
            placeholder="e.g. Acme merchant data"
            className={`${inputClass} mt-1`}
          />
        </label>
      </div>
      {fieldProblem && <p className="text-[11px] text-amber-400 -mt-2">{fieldProblem}</p>}

      <label className="block text-xs font-medium text-slate-300">
        Description <span className="text-slate-500 font-normal">(optional)</span>
        <textarea
          maxLength={500}
          rows={2}
          value={layout.description || ""}
          onChange={(e) => set({ description: e.target.value })}
          placeholder="What the acquirer uses it for, or where the spec lives"
          className={`${inputClass} mt-1 resize-y`}
        />
      </label>

      <fieldset className="space-y-2">
        <legend className="text-xs font-medium text-slate-300 mb-1">How the value is laid out</legend>
        <div className="grid sm:grid-cols-3 gap-2">
          {FORMATS.map((f) => (
            <label
              key={f.id}
              className={`cursor-pointer rounded-lg border px-3 py-2 ${
                layout.format === f.id ? "border-sky-500/60 bg-sky-500/10" : "border-white/10 hover:border-white/20"
              }`}
            >
              <input type="radio" name="format" value={f.id} checked={layout.format === f.id} onChange={() => set({ format: f.id, parts: [] })} className="sr-only" />
              <div className="text-xs font-semibold text-slate-100">{f.label}</div>
              <div className="text-[11px] text-slate-400">{f.hint}</div>
            </label>
          ))}
        </div>
      </fieldset>

      {layout.format === "tlv" && (
        <div className="space-y-2">
          <div className="flex flex-wrap gap-3">
            <label className="text-xs text-slate-300 flex items-center gap-2">
              Tag length
              <select value={layout.tagLength} onChange={(e) => set({ tagLength: Number(e.target.value), parts: layout.parts.map((p) => ({ ...p, tag: "" })) })} className="bg-slate-900/80 border border-white/10 rounded-md px-2 py-1 text-xs text-slate-100">
                {[1, 2, 3, 4].map((n) => (
                  <option key={n} value={n}>{n} characters</option>
                ))}
              </select>
            </label>
            <label className="text-xs text-slate-300 flex items-center gap-2">
              Length
              <select value={layout.lengthDigits} onChange={(e) => set({ lengthDigits: Number(e.target.value) })} className="bg-slate-900/80 border border-white/10 rounded-md px-2 py-1 text-xs text-slate-100">
                {[1, 2, 3].map((n) => (
                  <option key={n} value={n}>{n} digit{n > 1 ? "s" : ""}</option>
                ))}
              </select>
            </label>
          </div>
          <PartRows
            parts={layout.parts}
            keyLabel="Tag"
            keyOf={(p) => p.tag || ""}
            keyInput={(p, i) => (
              <input
                aria-label={`Tag ${i + 1}`}
                required
                maxLength={layout.tagLength}
                value={p.tag || ""}
                onChange={(e) => setPart(i, { tag: e.target.value })}
                placeholder={"0".repeat(layout.tagLength - 1) + "1"}
                className={`${inputClass} font-mono`}
              />
            )}
            onName={(i, name) => setPart(i, { name })}
            onRemove={(i) => set({ parts: layout.parts.filter((_, j) => j !== i) })}
          />
          <button type="button" onClick={() => set({ parts: [...layout.parts, { tag: "", name: "" }] })} className={smallButton}>
            <Plus className="w-3 h-3" />
            Name a tag
          </button>
          <p className="text-[11px] text-slate-500">Tags you don&apos;t name still show, as &quot;Tag 05&quot; and so on.</p>
        </div>
      )}

      {layout.format === "fixed" && (
        <div className="space-y-2">
          <PartRows
            parts={layout.parts}
            keyLabel="Length"
            keyOf={(p) => String(p.length ?? "")}
            keyInput={(p, i) => (
              <input
                aria-label={`Length of part ${i + 1}`}
                type="number"
                min={1}
                max={999}
                value={p.length ?? ""}
                onChange={(e) => setPart(i, { length: e.target.value ? Number(e.target.value) : null })}
                placeholder={i === layout.parts.length - 1 ? "rest" : ""}
                className={`${inputClass} font-mono`}
              />
            )}
            onName={(i, name) => setPart(i, { name })}
            onRemove={(i) => set({ parts: layout.parts.filter((_, j) => j !== i) })}
          />
          <button type="button" onClick={() => set({ parts: [...layout.parts, { name: "", length: null }] })} className={smallButton}>
            <Plus className="w-3 h-3" />
            Add a part
          </button>
          <p className="text-[11px] text-slate-500">Leave the last part&apos;s length empty to take the rest of the value.</p>
        </div>
      )}

      {layout.format !== "text" && (
        <div className="space-y-2 rounded-lg border border-white/10 bg-slate-900/40 p-3">
          <label className="block text-xs font-medium text-slate-300">
            Try it on a value
            <input
              value={sample}
              onChange={(e) => setSample(e.target.value)}
              placeholder="Paste a raw value of this field"
              className={`${inputClass} mt-1 font-mono text-xs`}
            />
          </label>
          {previewError && <p className="text-[11px] text-amber-400">{previewError}</p>}
          {preview && preview.subFields.length > 0 && (
            <table className="w-full text-[11px]">
              <tbody className="divide-y divide-white/5">
                {preview.subFields.map((s, i) => (
                  <tr key={i}>
                    <td className={`py-1 pr-3 align-top ${s.name.startsWith("Not matched") ? "text-amber-400" : "text-slate-400"}`}>{s.name}</td>
                    <td className="py-1 font-mono text-slate-100 break-all">{s.decodedValue}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {!sample && <p className="text-[11px] text-slate-500">No value of this field has been seen in this workspace yet.</p>}
        </div>
      )}

      {error && <p role="alert" className="text-xs text-rose-400">{error}</p>}
      <div className="flex items-center justify-end gap-2">
        <button type="button" onClick={onDone} className="px-3 py-2 text-xs text-slate-400 hover:text-slate-200">
          Cancel
        </button>
        <button
          type="submit"
          disabled={busy || !layout.name.trim() || (fieldProblem !== null && !fieldProblem.includes("replaces"))}
          className="px-4 py-2 rounded-lg bg-sky-500 hover:bg-sky-400 disabled:opacity-60 text-on-accent text-xs font-semibold"
        >
          {busy ? "Saving…" : "Save"}
        </button>
      </div>
    </form>
  );
};

const PartRows: React.FC<{
  parts: LayoutPart[];
  keyLabel: string;
  keyOf: (p: LayoutPart) => string;
  keyInput: (p: LayoutPart, i: number) => React.ReactNode;
  onName: (i: number, name: string) => void;
  onRemove: (i: number) => void;
}> = ({ parts, keyLabel, keyInput, onName, onRemove }) =>
  parts.length === 0 ? null : (
    <div className="space-y-1.5">
      <div className="grid grid-cols-[5.5rem_1fr_2rem] gap-2 text-[11px] text-slate-500">
        <span>{keyLabel}</span>
        <span>Name</span>
      </div>
      {parts.map((p, i) => (
        <div key={i} className="grid grid-cols-[5.5rem_1fr_2rem] gap-2 items-center">
          {keyInput(p, i)}
          <input
            aria-label={`Name of part ${i + 1}`}
            required
            maxLength={80}
            value={p.name}
            onChange={(e) => onName(i, e.target.value)}
            className={inputClass}
          />
          <button type="button" aria-label={`Remove part ${i + 1}`} onClick={() => onRemove(i)} className="p-1.5 rounded-md text-slate-400 hover:text-rose-400">
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      ))}
    </div>
  );

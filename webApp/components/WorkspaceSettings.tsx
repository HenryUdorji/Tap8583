"use client";

import React, { useCallback, useEffect, useState } from "react";
import { KeyRound, LogOut, Plus, Settings, Trash2, UserPlus, Users, X } from "lucide-react";
import { IngestKey, Invite, Me, Member, Workspace, request } from "@/lib/api";
import { ConnectTerminal } from "./ConnectTerminal";
import { CopyButton } from "./CopyButton";

export type SettingsTab = "terminals" | "people" | "workspace" | "account";

interface WorkspaceSettingsProps {
  isOpen: boolean;
  initialTab?: SettingsTab;
  onClose: () => void;
  serverUrl: string;
  me: Me;
  workspace: Workspace;
  onWorkspaceRenamed: (workspace: Workspace) => void;
  onLeftWorkspace: (workspaceId: string) => void;
  onNewWorkspace: () => void;
  onSignOut: () => void;
}

const inputClass =
  "w-full bg-slate-900/80 border border-white/10 focus:border-sky-500/50 rounded-lg px-3 py-2 text-sm text-slate-100 placeholder-slate-500 outline-none";
const smallButton =
  "inline-flex items-center gap-1 px-2 py-1 rounded-md border border-white/10 text-[11px] font-medium text-slate-300 hover:text-slate-100 disabled:opacity-50";

const when = (iso?: string | null) => (iso ? new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) : null);

/** Terminal keys, people and invites, workspace name, and the signed-in account. */
export const WorkspaceSettings: React.FC<WorkspaceSettingsProps> = ({
  isOpen,
  initialTab = "terminals",
  onClose,
  serverUrl,
  me,
  workspace,
  onWorkspaceRenamed,
  onLeftWorkspace,
  onNewWorkspace,
  onSignOut,
}) => {
  const [tab, setTab] = useState<SettingsTab>(initialTab);
  const [error, setError] = useState<string | null>(null);
  const isOwner = workspace.role === "owner";

  // Terminals
  const [keys, setKeys] = useState<IngestKey[] | null>(null);
  const [addingKey, setAddingKey] = useState(false);

  // People
  const [members, setMembers] = useState<Member[] | null>(null);
  const [invites, setInvites] = useState<Invite[]>([]);
  const [canEmail, setCanEmail] = useState(true);
  const [inviteEmail, setInviteEmail] = useState("");
  const [lastInvite, setLastInvite] = useState<{ email: string; link: string; emailed: boolean } | null>(null);

  // Workspace
  const [workspaceName, setWorkspaceName] = useState(workspace.name);

  useEffect(() => {
    if (isOpen) {
      setTab(initialTab);
      setError(null);
      setAddingKey(false);
      setLastInvite(null);
      setWorkspaceName(workspace.name);
    }
  }, [isOpen, initialTab, workspace.name]);

  useEffect(() => {
    if (!isOpen) return;
    const onKeyDown = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [isOpen, onClose]);

  const run = async (action: () => Promise<void>) => {
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong");
    }
  };

  const loadKeys = useCallback(
    () => run(async () => setKeys(await request<IngestKey[]>(serverUrl, `/workspaces/${workspace.id}/keys`))),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [serverUrl, workspace.id]
  );
  const loadPeople = useCallback(
    () =>
      run(async () => {
        const result = await request<{ members: Member[]; invites: Invite[]; canEmail: boolean }>(
          serverUrl,
          `/workspaces/${workspace.id}/members`
        );
        setMembers(result.members);
        setInvites(result.invites);
        setCanEmail(result.canEmail);
      }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [serverUrl, workspace.id]
  );

  useEffect(() => {
    if (!isOpen) return;
    if (tab === "terminals") loadKeys();
    if (tab === "people") loadPeople();
  }, [isOpen, tab, loadKeys, loadPeople]);

  if (!isOpen) return null;

  const tabs: { id: SettingsTab; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
    { id: "terminals", label: "Terminals", icon: KeyRound },
    { id: "people", label: "People", icon: Users },
    { id: "workspace", label: "Workspace", icon: Settings },
    { id: "account", label: "Account", icon: LogOut },
  ];

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-0 sm:p-4" role="dialog" aria-modal="true" aria-label="Workspace settings">
      <div className="bg-raised border border-white/10 sm:rounded-2xl w-full sm:max-w-2xl h-full sm:h-auto sm:max-h-[85vh] flex flex-col overflow-hidden shadow-2xl">
        <div className="px-5 py-4 border-b border-white/10 flex items-center justify-between gap-2">
          <div className="min-w-0">
            <div className="font-bold text-sm text-slate-100 truncate">{workspace.name}</div>
            <div className="text-[11px] text-slate-400">You are {isOwner ? "an owner" : "a member"} of this workspace</div>
          </div>
          <button onClick={onClose} aria-label="Close" className="p-1 rounded-lg hover:bg-slate-800 text-slate-400 hover:text-slate-200">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="flex gap-1 px-3 pt-2 border-b border-white/10 overflow-x-auto">
          {tabs.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              type="button"
              onClick={() => setTab(id)}
              aria-pressed={tab === id}
              className={`flex items-center gap-1.5 px-3 py-2 text-xs font-medium border-b-2 -mb-px whitespace-nowrap ${
                tab === id ? "border-sky-500 text-sky-400" : "border-transparent text-slate-400 hover:text-slate-200"
              }`}
            >
              <Icon className="w-3.5 h-3.5" />
              {label}
            </button>
          ))}
        </div>

        <div className="flex-1 overflow-y-auto p-5 space-y-4">
          {error && (
            <div role="alert" className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-xs text-rose-400">
              {error}
            </div>
          )}

          {tab === "terminals" && (
            <>
              {addingKey ? (
                <div className="space-y-3">
                  <ConnectTerminal serverUrl={serverUrl} workspace={workspace} waitForFirstEvent={false} onKeyCreated={() => loadKeys()} />
                  <button type="button" onClick={() => setAddingKey(false)} className="text-xs text-slate-400 hover:text-slate-200">
                    Done
                  </button>
                </div>
              ) : (
                <>
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-xs text-slate-400">Terminals send to this workspace with one of these keys. Revoke a key to cut off every app using it.</p>
                    {isOwner && (
                      <button type="button" onClick={() => setAddingKey(true)} className={`${smallButton} flex-shrink-0`}>
                        <Plus className="w-3 h-3" />
                        New key
                      </button>
                    )}
                  </div>
                  {keys === null ? (
                    <p className="text-xs text-slate-500">Loading…</p>
                  ) : keys.length === 0 ? (
                    <p className="text-xs text-slate-400">No keys yet. {isOwner ? "Create one to connect a terminal." : "Ask an owner to create one."}</p>
                  ) : (
                    <ul className="divide-y divide-white/5 rounded-lg border border-white/10">
                      {keys.map((k) => (
                        <li key={k.id} className="flex items-center gap-3 px-3 py-2.5">
                          <div className="min-w-0 flex-1">
                            <div className={`text-sm font-medium ${k.revokedAt ? "text-slate-500 line-through" : "text-slate-100"}`}>{k.name}</div>
                            <div className="text-[11px] text-slate-400 font-mono">
                              {k.prefix}… · created {when(k.createdAt)}
                              {k.revokedAt
                                ? ` · revoked ${when(k.revokedAt)}`
                                : k.lastUsedAt
                                ? ` · last used ${when(k.lastUsedAt)}`
                                : " · never used"}
                            </div>
                          </div>
                          {isOwner && !k.revokedAt && (
                            <button
                              type="button"
                              className={smallButton}
                              onClick={() => {
                                if (!window.confirm(`Revoke "${k.name}"? Apps using it stop sending straight away.`)) return;
                                run(async () => {
                                  await request(serverUrl, `/workspaces/${workspace.id}/keys/${k.id}`, { method: "DELETE" });
                                  await loadKeys();
                                });
                              }}
                            >
                              Revoke
                            </button>
                          )}
                        </li>
                      ))}
                    </ul>
                  )}
                </>
              )}
            </>
          )}

          {tab === "people" && (
            <>
              {isOwner && (
                <form
                  className="flex flex-col sm:flex-row gap-2"
                  onSubmit={(e) => {
                    e.preventDefault();
                    run(async () => {
                      const result = await request<{ invite: Invite; link: string; emailed: boolean }>(
                        serverUrl,
                        `/workspaces/${workspace.id}/invites`,
                        { method: "POST", body: JSON.stringify({ email: inviteEmail }) }
                      );
                      setLastInvite({ email: result.invite.email, link: result.link, emailed: result.emailed });
                      setInviteEmail("");
                      await loadPeople();
                    });
                  }}
                >
                  <input
                    type="email"
                    required
                    value={inviteEmail}
                    onChange={(e) => setInviteEmail(e.target.value)}
                    placeholder="teammate@company.com"
                    className={inputClass}
                  />
                  <button type="submit" className="inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg bg-sky-500 hover:bg-sky-400 text-on-accent text-xs font-semibold whitespace-nowrap">
                    <UserPlus className="w-3.5 h-3.5" />
                    Invite
                  </button>
                </form>
              )}
              {lastInvite && (
                <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 space-y-2 text-xs text-slate-200">
                  <div>
                    {lastInvite.emailed
                      ? `Invite emailed to ${lastInvite.email}. You can also send them this link:`
                      : `Email isn't set up on this server, so send ${lastInvite.email} this link yourself:`}
                  </div>
                  <div className="flex items-center gap-2">
                    <code className="flex-1 min-w-0 truncate font-mono text-[11px] bg-code border border-white/10 rounded px-2 py-1">{lastInvite.link}</code>
                    <CopyButton text={lastInvite.link} />
                  </div>
                </div>
              )}
              {!canEmail && !lastInvite && isOwner && (
                <p className="text-[11px] text-slate-500">Email isn&apos;t set up on this server, so you&apos;ll get a link to send yourself.</p>
              )}

              {members === null ? (
                <p className="text-xs text-slate-500">Loading…</p>
              ) : (
                <ul className="divide-y divide-white/5 rounded-lg border border-white/10">
                  {members.map((m) => {
                    const isMe = m.userId === me.user.id;
                    return (
                      <li key={m.userId} className="flex items-center gap-3 px-3 py-2.5">
                        <div className="w-7 h-7 rounded-full bg-sky-500/20 text-sky-400 flex items-center justify-center text-xs font-bold flex-shrink-0">
                          {(m.name || m.email)[0]?.toUpperCase()}
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="text-sm text-slate-100 truncate">
                            {m.name || m.email}
                            {isMe && <span className="text-slate-400"> (you)</span>}
                          </div>
                          <div className="text-[11px] text-slate-400 truncate">
                            {m.name ? `${m.email} · ` : ""}
                            {m.role === "owner" ? "Owner" : "Member"}
                          </div>
                        </div>
                        {(isOwner || isMe) && (
                          <button
                            type="button"
                            className={smallButton}
                            onClick={() => {
                              const question = isMe
                                ? `Leave ${workspace.name}? You'll need a new invite to come back.`
                                : `Remove ${m.name || m.email} from ${workspace.name}?`;
                              if (!window.confirm(question)) return;
                              run(async () => {
                                await request(serverUrl, `/workspaces/${workspace.id}/members/${m.userId}`, { method: "DELETE" });
                                if (isMe) onLeftWorkspace(workspace.id);
                                else await loadPeople();
                              });
                            }}
                          >
                            {isMe ? "Leave" : "Remove"}
                          </button>
                        )}
                      </li>
                    );
                  })}
                  {invites.map((inv) => (
                    <li key={inv.id} className="flex items-center gap-3 px-3 py-2.5">
                      <div className="w-7 h-7 rounded-full border border-dashed border-white/20 flex-shrink-0" />
                      <div className="min-w-0 flex-1">
                        <div className="text-sm text-slate-300 truncate">{inv.email}</div>
                        <div className="text-[11px] text-slate-500">Invited · expires {when(inv.expiresAt)}</div>
                      </div>
                      {isOwner && (
                        <button
                          type="button"
                          className={smallButton}
                          onClick={() =>
                            run(async () => {
                              await request(serverUrl, `/workspaces/${workspace.id}/invites/${inv.id}`, { method: "DELETE" });
                              await loadPeople();
                            })
                          }
                        >
                          <Trash2 className="w-3 h-3" />
                          Cancel
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}

          {tab === "workspace" && (
            <>
              <form
                className="space-y-2"
                onSubmit={(e) => {
                  e.preventDefault();
                  run(async () => {
                    const updated = await request<Workspace>(serverUrl, `/workspaces/${workspace.id}`, {
                      method: "PATCH",
                      body: JSON.stringify({ name: workspaceName }),
                    });
                    onWorkspaceRenamed(updated);
                  });
                }}
              >
                <label className="block text-xs font-medium text-slate-300">
                  Workspace name
                  <input
                    value={workspaceName}
                    onChange={(e) => setWorkspaceName(e.target.value)}
                    disabled={!isOwner}
                    maxLength={80}
                    className={`mt-1 ${inputClass} disabled:opacity-60`}
                  />
                </label>
                {isOwner && (
                  <button
                    type="submit"
                    disabled={!workspaceName.trim() || workspaceName.trim() === workspace.name}
                    className="px-3 py-1.5 rounded-lg bg-sky-500 hover:bg-sky-400 disabled:opacity-50 text-on-accent text-xs font-semibold"
                  >
                    Save name
                  </button>
                )}
              </form>
              <div className="pt-2 border-t border-white/10">
                <button type="button" onClick={onNewWorkspace} className={smallButton}>
                  <Plus className="w-3 h-3" />
                  Create another workspace
                </button>
              </div>
            </>
          )}

          {tab === "account" && (
            <div className="space-y-3">
              <div className="text-sm text-slate-100">{me.user.name || me.user.email}</div>
              {me.user.name && <div className="text-xs text-slate-400 -mt-2">{me.user.email}</div>}
              <p className="text-xs text-slate-400">You sign in with a link sent to this email address.</p>
              <button
                type="button"
                onClick={onSignOut}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-white/10 text-xs font-medium text-slate-300 hover:text-slate-100"
              >
                <LogOut className="w-3.5 h-3.5" />
                Sign out
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

"use client";

import React, { useCallback, useEffect, useState } from "react";
import { ChevronDown } from "lucide-react";
import {
  ApiError,
  AuthConfig,
  Me,
  Workspace,
  loadLastWorkspace,
  loadPendingInvite,
  request,
  saveLastWorkspace,
  savePendingInvite,
} from "@/lib/api";
import {
  loadApiKey,
  loadServerUrlOverride,
  normaliseServerUrl,
  resolveServerUrl,
  saveApiKey,
  saveServerUrlOverride,
} from "@/lib/connection";
import { Dashboard } from "./Dashboard";
import { SettingsModal } from "./SettingsModal";
import { AuthLayout, CreateWorkspace, InviteScreen, SignIn, SigningIn } from "./AccountScreens";
import { ConnectTerminal } from "./ConnectTerminal";
import { SettingsTab, WorkspaceSettings } from "./WorkspaceSettings";

/** Asks the server how viewers sign in, then shows the matching shell. */
export function App() {
  const [serverUrlOverride, setServerUrlOverride] = useState("");
  const [serverUrl, setServerUrl] = useState("");
  const [authConfig, setAuthConfig] = useState<AuthConfig | null>(null);

  useEffect(() => {
    const override = loadServerUrlOverride();
    setServerUrlOverride(override);
    setServerUrl(resolveServerUrl(override));
  }, []);

  useEffect(() => {
    if (!serverUrl) return;
    let active = true;
    setAuthConfig(null);
    request<AuthConfig>(serverUrl, "/auth/config")
      .then((config) => active && setAuthConfig(config))
      // Older servers, or a server that can't be reached yet: fall back to the shared key.
      .catch(() => active && setAuthConfig({ mode: "key", emailSignIn: false }));
    return () => {
      active = false;
    };
  }, [serverUrl]);

  const changeServer = (override: string) => {
    saveServerUrlOverride(override);
    const normalised = override ? normaliseServerUrl(override) : "";
    setServerUrlOverride(normalised);
    setServerUrl(resolveServerUrl(normalised));
  };

  if (!serverUrl || !authConfig) return <div className="min-h-screen bg-app" />;
  if (authConfig.mode === "accounts") return <AccountsApp serverUrl={serverUrl} authConfig={authConfig} />;
  return <KeyModeApp serverUrl={serverUrl} serverUrlOverride={serverUrlOverride} onServerChange={changeServer} />;
}

/** The original setup: everyone pastes the shared viewer key. */
function KeyModeApp({
  serverUrl,
  serverUrlOverride,
  onServerChange,
}: {
  serverUrl: string;
  serverUrlOverride: string;
  onServerChange: (override: string) => void;
}) {
  const [apiKey, setApiKey] = useState<string | null>(null);
  // Bumped on every settings save so re-saving the same key retries the connection.
  const [connectNonce, setConnectNonce] = useState(0);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  useEffect(() => {
    const saved = loadApiKey();
    setApiKey(saved);
    if (!saved) setIsSettingsOpen(true);
  }, []);

  if (apiKey === null) return <div className="min-h-screen bg-app" />;

  return (
    <>
      <Dashboard
        serverUrl={serverUrl}
        access={apiKey ? { mode: "key", apiKey } : null}
        connectNonce={connectNonce}
        onUnauthorized={() => setIsSettingsOpen(true)}
        onOpenSettings={() => setIsSettingsOpen(true)}
      />
      <SettingsModal
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        apiKey={apiKey}
        serverUrlOverride={serverUrlOverride}
        defaultServerUrl={serverUrlOverride ? "" : serverUrl}
        onSave={(key, urlOverride) => {
          saveApiKey(key);
          setApiKey(key.trim());
          setConnectNonce((n) => n + 1);
          if (normaliseServerUrl(urlOverride) !== serverUrlOverride) onServerChange(urlOverride);
        }}
      />
    </>
  );
}

/** Removes sign-in and invite parameters from the address bar once they've been read. */
function stripParams(...names: string[]) {
  const url = new URL(window.location.href);
  names.forEach((n) => url.searchParams.delete(n));
  window.history.replaceState(null, "", `${url.pathname}${url.search}${url.hash}`);
}

/** Each person signs in with an emailed link and sees their own workspaces. */
function AccountsApp({ serverUrl, authConfig }: { serverUrl: string; authConfig: AuthConfig }) {
  // undefined while loading, null when signed out.
  const [me, setMe] = useState<Me | null | undefined>(undefined);
  const [verifying, setVerifying] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [invite, setInvite] = useState<{ token: string; workspaceName: string } | null>(null);
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [workspaceId, setWorkspaceId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [onboarding, setOnboarding] = useState<Workspace | null>(null);
  const [settings, setSettings] = useState<{ open: boolean; tab: SettingsTab }>({ open: false, tab: "terminals" });

  const loadMe = useCallback(async () => {
    try {
      setMe(await request<Me>(serverUrl, "/me"));
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) setMe(null);
      else {
        setAuthError("Can't reach the Tap8583 server right now.");
        setMe(null);
      }
    }
  }, [serverUrl]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const emailToken = params.get("emailToken");
    const inviteToken = params.get("invite");
    if (inviteToken) savePendingInvite(inviteToken);
    stripParams("emailToken", "invite");

    const pending = loadPendingInvite();
    if (pending) {
      request<{ workspaceName: string }>(serverUrl, `/invites/${encodeURIComponent(pending)}`)
        .then((details) => setInvite({ token: pending, workspaceName: details.workspaceName }))
        .catch((e) => {
          savePendingInvite(null);
          setInviteError(e instanceof Error ? e.message : "This invite is no longer valid");
        });
    }

    if (emailToken) {
      setVerifying(true);
      request<Me>(serverUrl, "/auth/email/verify", { method: "POST", body: JSON.stringify({ token: emailToken }) })
        .then(setMe)
        .catch(async (e) => {
          setAuthError(e instanceof Error ? e.message : "Could not sign you in");
          // An older valid session still counts.
          await loadMe();
        })
        .finally(() => setVerifying(false));
    } else {
      loadMe();
    }
  }, [serverUrl, loadMe]);

  // Keep the selected workspace valid as memberships change.
  useEffect(() => {
    if (!me) return;
    const ids = me.workspaces.map((w) => w.id);
    if (workspaceId && ids.includes(workspaceId)) return;
    const last = loadLastWorkspace();
    setWorkspaceId(last && ids.includes(last) ? last : ids[0] || null);
  }, [me, workspaceId]);

  const selectWorkspace = (id: string) => {
    setWorkspaceId(id);
    saveLastWorkspace(id);
  };

  const signOut = async () => {
    try {
      await request(serverUrl, "/auth/logout", { method: "POST" });
    } catch {
      // the cookie may already be gone
    }
    setSettings((s) => ({ ...s, open: false }));
    setWorkspaceId(null);
    setAuthError(null);
    setMe(null);
  };

  if (verifying || me === undefined) return <SigningIn />;

  if (me === null) {
    return (
      <SignIn
        serverUrl={serverUrl}
        emailSignIn={authConfig.emailSignIn}
        next={invite ? `/?invite=${encodeURIComponent(invite.token)}` : undefined}
        invitedTo={invite?.workspaceName}
        error={authError || inviteError}
      />
    );
  }

  if (invite) {
    const done = () => {
      savePendingInvite(null);
      setInvite(null);
    };
    return (
      <InviteScreen
        serverUrl={serverUrl}
        token={invite.token}
        workspaceName={invite.workspaceName}
        user={me.user}
        onJoined={(workspace) => {
          done();
          setMe({ ...me, workspaces: [...me.workspaces.filter((w) => w.id !== workspace.id), workspace] });
          selectWorkspace(workspace.id);
        }}
        onDecline={done}
      />
    );
  }

  if (inviteError) {
    return (
      <AuthLayout title="Invite not valid" subtitle={inviteError}>
        <p className="text-sm text-slate-400">Ask whoever invited you to send a new link.</p>
        <button
          type="button"
          onClick={() => setInviteError(null)}
          className="w-full px-4 py-2 rounded-lg bg-sky-500 hover:bg-sky-400 text-on-accent text-sm font-semibold"
        >
          Continue
        </button>
      </AuthLayout>
    );
  }

  if (creating || me.workspaces.length === 0) {
    return (
      <CreateWorkspace
        serverUrl={serverUrl}
        me={me}
        onCancel={me.workspaces.length ? () => setCreating(false) : undefined}
        onCreated={(workspace, nextMe) => {
          setMe(nextMe);
          selectWorkspace(workspace.id);
          setCreating(false);
          setOnboarding(workspace);
        }}
      />
    );
  }

  if (onboarding) {
    return (
      <AuthLayout
        wide
        title="Connect your first terminal"
        subtitle={`Add the listener to your POS app and its ISO 8583 traffic shows up in ${onboarding.name}.`}
      >
        <ConnectTerminal serverUrl={serverUrl} workspace={onboarding} waitForFirstEvent />
        <div className="flex items-center justify-between gap-3 pt-1">
          <button
            type="button"
            onClick={() => {
              setOnboarding(null);
              setSettings({ open: true, tab: "people" });
            }}
            className="text-xs text-slate-400 hover:text-slate-200"
          >
            Invite your team
          </button>
          <button
            type="button"
            onClick={() => setOnboarding(null)}
            className="px-4 py-2 rounded-lg bg-sky-500 hover:bg-sky-400 text-on-accent text-sm font-semibold"
          >
            Open dashboard
          </button>
        </div>
      </AuthLayout>
    );
  }

  const workspace = me.workspaces.find((w) => w.id === workspaceId);
  if (!workspace) return <SigningIn />;

  const switcher = (
    <label className="relative flex items-center min-w-0">
      <span className="sr-only">Workspace</span>
      <select
        value={workspace.id}
        onChange={(e) => {
          if (e.target.value === "__new") setCreating(true);
          else selectWorkspace(e.target.value);
        }}
        className="appearance-none max-w-[9rem] sm:max-w-[14rem] truncate bg-slate-900/80 border border-white/10 hover:border-white/20 rounded-lg pl-2.5 pr-7 py-1 text-xs font-medium text-slate-200 outline-none focus:border-sky-500/50"
      >
        {me.workspaces.map((w) => (
          <option key={w.id} value={w.id}>
            {w.name}
          </option>
        ))}
        <option value="__new">+ New workspace</option>
      </select>
      <ChevronDown className="pointer-events-none absolute right-2 w-3.5 h-3.5 text-slate-400" />
    </label>
  );

  return (
    <>
      <Dashboard
        serverUrl={serverUrl}
        access={{ mode: "accounts", workspaceId: workspace.id }}
        onUnauthorized={loadMe}
        onOpenSettings={() => setSettings({ open: true, tab: "terminals" })}
        topBarExtra={switcher}
        emptyAction={{ label: "Connect a terminal", onClick: () => setSettings({ open: true, tab: "terminals" }) }}
        clearAudience="everyone in this workspace"
      />
      <WorkspaceSettings
        isOpen={settings.open}
        initialTab={settings.tab}
        onClose={() => setSettings((s) => ({ ...s, open: false }))}
        serverUrl={serverUrl}
        me={me}
        workspace={workspace}
        onWorkspaceRenamed={(renamed) =>
          setMe({ ...me, workspaces: me.workspaces.map((w) => (w.id === renamed.id ? renamed : w)) })
        }
        onLeftWorkspace={(id) => {
          setSettings((s) => ({ ...s, open: false }));
          if (loadLastWorkspace() === id) saveLastWorkspace(null);
          setMe({ ...me, workspaces: me.workspaces.filter((w) => w.id !== id) });
        }}
        onNewWorkspace={() => {
          setSettings((s) => ({ ...s, open: false }));
          setCreating(true);
        }}
        onSignOut={signOut}
      />
    </>
  );
}

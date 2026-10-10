"use client";

import React, { useState } from "react";
import { CreditCard, Mail, Users } from "lucide-react";
import { Me, User, Workspace, request } from "@/lib/api";
import { ThemeToggle } from "./ThemeToggle";

/** Centered card used by the sign-in, invite and onboarding screens. */
export const AuthLayout: React.FC<{ title: string; subtitle?: React.ReactNode; children: React.ReactNode; wide?: boolean }> = ({
  title,
  subtitle,
  children,
  wide,
}) => (
  <div className="min-h-screen bg-app text-slate-100 flex flex-col">
    <div className="flex items-center justify-between px-4 py-3">
      <div className="flex items-center gap-2">
        <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-sky-500 to-indigo-600 flex items-center justify-center">
          <CreditCard className="w-4 h-4 text-on-accent" />
        </div>
        <span className="font-bold tracking-tight text-sm text-white">Tap8583</span>
      </div>
      <ThemeToggle />
    </div>
    <div className="flex-1 flex items-start sm:items-center justify-center px-4 pb-10 pt-6 sm:pt-0">
      <div className={`w-full ${wide ? "max-w-xl" : "max-w-sm"} bg-raised border border-white/10 rounded-2xl shadow-2xl p-6 space-y-4`}>
        <div className="space-y-1">
          <h1 className="text-lg font-bold text-slate-100">{title}</h1>
          {subtitle && <p className="text-sm text-slate-400">{subtitle}</p>}
        </div>
        {children}
      </div>
    </div>
  </div>
);

const inputClass =
  "mt-1 w-full bg-slate-900/80 border border-white/10 focus:border-sky-500/50 rounded-lg px-3 py-2 text-sm text-slate-100 placeholder-slate-500 outline-none";
const primaryButton =
  "w-full px-4 py-2 rounded-lg bg-sky-500 hover:bg-sky-400 disabled:opacity-60 text-on-accent text-sm font-semibold";

interface SignInProps {
  serverUrl: string;
  emailSignIn: boolean;
  /** Where the emailed link should land, e.g. "/?invite=…". */
  next?: string;
  error?: string | null;
  invitedTo?: string | null;
}

/** Email in, link out. No passwords. */
export const SignIn: React.FC<SignInProps> = ({ serverUrl, emailSignIn, next, error: initialError, invitedTo }) => {
  const [email, setEmail] = useState("");
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(initialError || null);
  const [busy, setBusy] = useState(false);

  const send = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await request(serverUrl, "/auth/email", { method: "POST", body: JSON.stringify({ email, next }) });
      setSentTo(email.trim());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send the link");
    } finally {
      setBusy(false);
    }
  };

  if (sentTo) {
    return (
      <AuthLayout title="Check your email" subtitle={<>We sent a sign-in link to <span className="font-semibold text-slate-200">{sentTo}</span>.</>}>
        <div className="flex items-start gap-3 rounded-lg border border-sky-500/30 bg-sky-500/10 p-3 text-xs text-slate-300">
          <Mail className="w-4 h-4 mt-0.5 text-sky-400 flex-shrink-0" />
          <span>Open the link on this device. It works once and expires in 15 minutes. Check spam if it hasn&apos;t arrived in a minute.</span>
        </div>
        {error && <p role="alert" className="text-xs text-rose-400">{error}</p>}
        <div className="flex items-center justify-between text-xs">
          <button type="button" onClick={() => setSentTo(null)} className="text-slate-400 hover:text-slate-200">
            Use a different email
          </button>
          <button type="button" onClick={() => send()} disabled={busy} className="text-sky-400 hover:text-sky-300 font-medium">
            {busy ? "Sending…" : "Send it again"}
          </button>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title={invitedTo ? `Join ${invitedTo}` : "Sign in to Tap8583"}
      subtitle={
        invitedTo
          ? "You've been invited to a workspace. Enter your email and we'll send you a link to join."
          : "Enter your email and we'll send you a link. No password needed."
      }
    >
      {emailSignIn ? (
        <form onSubmit={send} className="space-y-3">
          <label className="block text-xs font-medium text-slate-300">
            Email
            <input
              type="email"
              required
              autoFocus
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@company.com"
              className={inputClass}
            />
          </label>
          {error && <p role="alert" className="text-xs text-rose-400">{error}</p>}
          <button type="submit" disabled={busy} className={primaryButton}>
            {busy ? "Sending…" : "Email me a sign-in link"}
          </button>
        </form>
      ) : (
        <p className="text-sm text-slate-300">
          Email isn&apos;t set up on this server yet, so nobody can sign in. Whoever runs it needs to set the SMTP settings
          (see HETZNER_DEPLOYMENT.md).
        </p>
      )}
    </AuthLayout>
  );
};

/** Shown while an emailed link is being confirmed. */
export const SigningIn: React.FC = () => (
  <AuthLayout title="Signing you in…">
    <div className="h-1 w-full rounded bg-slate-800 overflow-hidden">
      <div className="h-full w-1/3 bg-sky-500 animate-pulse" />
    </div>
  </AuthLayout>
);

interface InviteScreenProps {
  serverUrl: string;
  token: string;
  workspaceName: string;
  user: User;
  onJoined: (workspace: Workspace) => void;
  onDecline: () => void;
}

export const InviteScreen: React.FC<InviteScreenProps> = ({ serverUrl, token, workspaceName, user, onJoined, onDecline }) => {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const join = async () => {
    setBusy(true);
    setError(null);
    try {
      onJoined(await request<Workspace>(serverUrl, "/invites/accept", { method: "POST", body: JSON.stringify({ token }) }));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not join");
      setBusy(false);
    }
  };

  return (
    <AuthLayout title={`Join ${workspaceName}`} subtitle={<>You&apos;re signed in as {user.email}.</>}>
      <div className="flex items-start gap-3 rounded-lg border border-white/10 bg-slate-900/60 p-3 text-xs text-slate-300">
        <Users className="w-4 h-4 mt-0.5 text-sky-400 flex-shrink-0" />
        <span>You&apos;ll see this workspace&apos;s terminals and transactions live, alongside the rest of the team.</span>
      </div>
      {error && <p role="alert" className="text-xs text-rose-400">{error}</p>}
      <button type="button" onClick={join} disabled={busy} className={primaryButton}>
        {busy ? "Joining…" : `Join ${workspaceName}`}
      </button>
      <button type="button" onClick={onDecline} className="w-full text-xs text-slate-400 hover:text-slate-200">
        Not now
      </button>
    </AuthLayout>
  );
};

interface CreateWorkspaceProps {
  serverUrl: string;
  me: Me;
  onCreated: (workspace: Workspace, me: Me) => void;
  onCancel?: () => void;
}

/** First-run step (and "New workspace"): name the workspace, and yourself if needed. */
export const CreateWorkspace: React.FC<CreateWorkspaceProps> = ({ serverUrl, me, onCreated, onCancel }) => {
  const [name, setName] = useState("");
  const [yourName, setYourName] = useState(me.user.name || "");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const askName = !me.user.name;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      let user = me.user;
      if (askName && yourName.trim()) {
        user = await request<User>(serverUrl, "/me", { method: "PATCH", body: JSON.stringify({ name: yourName }) });
      }
      const workspace = await request<Workspace>(serverUrl, "/workspaces", { method: "POST", body: JSON.stringify({ name }) });
      onCreated(workspace, { user, workspaces: [...me.workspaces, workspace] });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create the workspace");
      setBusy(false);
    }
  };

  return (
    <AuthLayout
      title={me.workspaces.length ? "New workspace" : "Create your workspace"}
      subtitle="A workspace holds your team's terminals and transactions. You can invite people next."
    >
      <form onSubmit={submit} className="space-y-3">
        {askName && (
          <label className="block text-xs font-medium text-slate-300">
            Your name
            <input value={yourName} onChange={(e) => setYourName(e.target.value)} maxLength={80} placeholder="Ada Obi" className={inputClass} />
          </label>
        )}
        <label className="block text-xs font-medium text-slate-300">
          Workspace name
          <input
            required
            autoFocus
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={80}
            placeholder="e.g. Acme POS QA"
            className={inputClass}
          />
        </label>
        {error && <p role="alert" className="text-xs text-rose-400">{error}</p>}
        <button type="submit" disabled={busy || !name.trim()} className={primaryButton}>
          {busy ? "Creating…" : "Create workspace"}
        </button>
        {onCancel && (
          <button type="button" onClick={onCancel} className="w-full text-xs text-slate-400 hover:text-slate-200">
            Cancel
          </button>
        )}
      </form>
    </AuthLayout>
  );
};

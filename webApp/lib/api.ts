// How the dashboard proves who it is to the Tap8583 server.
//
// "key" mode: the original shared viewer key, sent as x-api-key.
// "accounts" mode: a session cookie set by the email sign-in link, plus the
// workspace being viewed, sent as ?workspace=.

import { apiUrl } from "@/lib/connection";

export type Access =
  | { mode: "key"; apiKey: string }
  | { mode: "accounts"; workspaceId: string };

export interface AuthConfig {
  mode: "accounts" | "key";
  emailSignIn: boolean;
}

export interface User {
  id: string;
  email: string;
  name?: string | null;
  avatarUrl?: string | null;
}

export interface Workspace {
  id: string;
  name: string;
  role: "owner" | "member";
}

export interface Me {
  user: User;
  workspaces: Workspace[];
}

export interface Member {
  userId: string;
  email: string;
  name?: string | null;
  role: "owner" | "member";
  joinedAt: string;
}

export interface Invite {
  id: string;
  email: string;
  createdAt: string;
  expiresAt: string;
}

export interface IngestKey {
  id: string;
  name: string;
  prefix: string;
  createdAt: string;
  lastUsedAt?: string | null;
  revokedAt?: string | null;
}

/** Thrown for non-2xx responses; [message] is the server's explanation when it gave one. */
export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

/** Plain JSON request with the session cookie (accounts mode routes). */
export async function request<T>(serverUrl: string, path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(apiUrl(serverUrl, path), {
    ...init,
    credentials: "include",
    headers: {
      ...(init.body && typeof init.body === "string" ? { "Content-Type": "application/json" } : {}),
      ...init.headers,
    },
  });
  if (!res.ok) {
    let message = `Request failed (HTTP ${res.status})`;
    try {
      const body = await res.json();
      if (body?.error) message = body.error;
    } catch {
      // not JSON
    }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

/** A dashboard data request (devices, transactions, clear, events, decode) for the current access. */
export function accessFetch(serverUrl: string, access: Access, path: string, init: RequestInit = {}): Promise<Response> {
  if (access.mode === "key") {
    return fetch(apiUrl(serverUrl, path), {
      ...init,
      headers: { ...init.headers, "x-api-key": access.apiKey },
    });
  }
  const sep = path.includes("?") ? "&" : "?";
  return fetch(apiUrl(serverUrl, `${path}${sep}workspace=${encodeURIComponent(access.workspaceId)}`), {
    ...init,
    credentials: "include",
  });
}

/** First WebSocket frame. In accounts mode the session cookie travels with the upgrade request. */
export function streamAuthFrame(access: Access, serial: string): string {
  return JSON.stringify(
    access.mode === "key"
      ? { type: "AUTH", apiKey: access.apiKey, serial }
      : { type: "AUTH", workspace: access.workspaceId, serial }
  );
}

const WORKSPACE_STORAGE = "tap8583_workspace";
const INVITE_STORAGE = "tap8583_pending_invite";

function read(storage: () => Storage, key: string): string | null {
  try {
    return storage().getItem(key);
  } catch {
    return null;
  }
}

function write(storage: () => Storage, key: string, value: string | null) {
  try {
    if (value) storage().setItem(key, value);
    else storage().removeItem(key);
  } catch {
    // storage unavailable
  }
}

export const loadLastWorkspace = () => read(() => window.localStorage, WORKSPACE_STORAGE);
export const saveLastWorkspace = (id: string | null) => write(() => window.localStorage, WORKSPACE_STORAGE, id);

/** An invite link opened before signing in, kept until it is accepted or declined. */
export const loadPendingInvite = () => read(() => window.sessionStorage, INVITE_STORAGE);
export const savePendingInvite = (token: string | null) => write(() => window.sessionStorage, INVITE_STORAGE, token);

/** Published listener version shown in the install snippet. */
export const LISTENER_VERSION = "1.0.0";

/** How a workspace's acquirer lays out one private field (see FieldLayouts.kt). */
export interface LayoutPart {
  tag?: string | null;
  name: string;
  length?: number | null;
}

export interface FieldLayout {
  field: number;
  name: string;
  description?: string | null;
  format: "text" | "tlv" | "fixed";
  tagLength: number;
  lengthDigits: number;
  parts: LayoutPart[];
}

export interface FieldPreview {
  decodedValue?: string | null;
  subFields: { name: string; rawValue: string; decodedValue: string }[];
}

/** A link that opens one transaction in this dashboard. */
export function transactionLink(access: Access, transactionId: string): string {
  const url = new URL(window.location.origin + window.location.pathname);
  if (access.mode === "accounts") url.searchParams.set("workspace", access.workspaceId);
  url.searchParams.set("tx", transactionId);
  return url.toString();
}

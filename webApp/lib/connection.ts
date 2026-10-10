// Connection settings for the Tap8583 server.
//
// The server URL defaults to the site the dashboard is served from (the
// Hetzner deployment puts the dashboard and the API behind one Caddy host).
// For local development set NEXT_PUBLIC_SERVER_URL=http://localhost:8080.
// The API key is never baked into the bundle: each viewer enters it once and
// it is kept in this browser's localStorage.

export type ConnectionState = "NO_KEY" | "CONNECTING" | "LIVE" | "OFFLINE" | "UNAUTHORIZED";

/** WebSocket close code the server uses for a missing or wrong key. */
export const WS_CLOSE_UNAUTHORIZED = 4401;

const KEY_STORAGE = "tap8583_api_key";
const URL_STORAGE = "tap8583_server_url";

function readStorage(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string) {
  try {
    if (value) window.localStorage.setItem(key, value);
    else window.localStorage.removeItem(key);
  } catch {
    // Storage unavailable (private mode): the value lasts for this tab only.
  }
}

/** Normalises a server URL to an http(s) origin + path with no trailing slash. */
export function normaliseServerUrl(url: string): string {
  return url
    .trim()
    .replace(/^ws:\/\//, "http://")
    .replace(/^wss:\/\//, "https://")
    .replace(/\/ws\/stream\/?$/, "")
    .replace(/\/api\/v1(\/events)?\/?$/, "")
    .replace(/\/+$/, "");
}

export function loadApiKey(): string {
  return readStorage(KEY_STORAGE) || "";
}

export function saveApiKey(key: string) {
  writeStorage(KEY_STORAGE, key.trim());
}

/** The user's explicit server URL override, or "" to use the default. */
export function loadServerUrlOverride(): string {
  const saved = readStorage(URL_STORAGE);
  return saved ? normaliseServerUrl(saved) : "";
}

export function saveServerUrlOverride(url: string) {
  writeStorage(URL_STORAGE, url ? normaliseServerUrl(url) : "");
}

/** Resolves the server base URL: override → build-time env → this site. */
export function resolveServerUrl(override: string): string {
  if (override) return normaliseServerUrl(override);
  if (process.env.NEXT_PUBLIC_SERVER_URL) return normaliseServerUrl(process.env.NEXT_PUBLIC_SERVER_URL);
  return window.location.origin;
}

export function apiUrl(serverUrl: string, path: string): string {
  return `${serverUrl}/api/v1${path}`;
}

export function streamUrl(serverUrl: string): string {
  return `${serverUrl.replace(/^http/, "ws")}/ws/stream`;
}

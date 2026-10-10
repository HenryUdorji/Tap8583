-- Accounts, workspaces and stored transaction history.

CREATE TABLE users (
    id          uuid PRIMARY KEY,
    email       text NOT NULL,
    name        text,
    avatar_url  text,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX users_email_idx ON users (lower(email));

-- Browser sessions. Only the SHA-256 of the cookie value is stored.
CREATE TABLE sessions (
    token_hash    text PRIMARY KEY,
    user_id       uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at    timestamptz NOT NULL DEFAULT now(),
    expires_at    timestamptz NOT NULL,
    last_seen_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX sessions_user_idx ON sessions (user_id);

-- Single-use email sign-in links.
CREATE TABLE email_login_tokens (
    token_hash  text PRIMARY KEY,
    email       text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz
);

CREATE TABLE workspaces (
    id          uuid PRIMARY KEY,
    name        text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE memberships (
    workspace_id  uuid NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id       uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role          text NOT NULL CHECK (role IN ('owner', 'member')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, user_id)
);
CREATE INDEX memberships_user_idx ON memberships (user_id);

CREATE TABLE invites (
    id            uuid PRIMARY KEY,
    workspace_id  uuid NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    email         text NOT NULL,
    token_hash    text NOT NULL UNIQUE,
    invited_by    uuid REFERENCES users (id) ON DELETE SET NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    expires_at    timestamptz NOT NULL,
    accepted_at   timestamptz,
    accepted_by   uuid REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX invites_workspace_idx ON invites (workspace_id);

-- Keys terminals send with. Only the SHA-256 is stored; the prefix is for display.
CREATE TABLE ingest_keys (
    id            uuid PRIMARY KEY,
    workspace_id  uuid NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name          text NOT NULL,
    key_hash      text NOT NULL UNIQUE,
    key_prefix    text NOT NULL,
    created_by    uuid REFERENCES users (id) ON DELETE SET NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    last_used_at  timestamptz,
    revoked_at    timestamptz
);
CREATE INDEX ingest_keys_workspace_idx ON ingest_keys (workspace_id);

-- Decoded transactions, stored as the JSON the dashboard receives.
CREATE TABLE transactions (
    id            text PRIMARY KEY,
    workspace_id  uuid NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    serial        text NOT NULL,
    stan          text NOT NULL,
    status        text NOT NULL,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    data          jsonb NOT NULL
);
CREATE INDEX transactions_workspace_created_idx ON transactions (workspace_id, created_at DESC);
CREATE INDEX transactions_workspace_serial_idx ON transactions (workspace_id, serial, created_at DESC);

CREATE TABLE channel_events (
    id            text PRIMARY KEY,
    workspace_id  uuid NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    serial        text NOT NULL,
    captured_at   timestamptz NOT NULL,
    data          jsonb NOT NULL
);
CREATE INDEX channel_events_workspace_idx ON channel_events (workspace_id, captured_at DESC);

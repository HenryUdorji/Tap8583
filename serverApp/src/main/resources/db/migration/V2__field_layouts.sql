-- How a workspace's acquirer lays out its private fields (DE48, DE62, ...).
-- Applied when transactions are read, so edits also explain stored history.
CREATE TABLE field_layouts (
    workspace_id  uuid NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    field_number  int  NOT NULL,
    layout        jsonb NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, field_number)
);

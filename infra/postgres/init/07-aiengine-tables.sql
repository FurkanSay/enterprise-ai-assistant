-- AI Engine schema tables.
-- Sessions, messages (the conversation log), and tool_invocations (audit).

-- ─── sessions ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS aiengine_schema.sessions (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL REFERENCES identity_schema.tenants(id) ON DELETE RESTRICT,
    user_id                  UUID NOT NULL,  -- references identity_schema.users.id (no cross-schema FK)
    title                    TEXT NOT NULL DEFAULT '',
    model                    TEXT NOT NULL DEFAULT '',
    message_count            INT NOT NULL DEFAULT 0,
    compaction_count         INT NOT NULL DEFAULT 0,
    compaction_summary       TEXT,
    last_heartbeat_at        TIMESTAMPTZ,
    archived_at              TIMESTAMPTZ,
    -- Conversation forking — when a session is created by /sessions/{id}/fork,
    -- these track the parent for sidebar lineage rendering. NULL on root sessions.
    parent_session_id        UUID REFERENCES aiengine_schema.sessions(id) ON DELETE SET NULL,
    forked_from_message_id   UUID,  -- the assistant message the user forked from
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Existing deployments: the two fork columns were added in Phase K. The
-- ALTER below is a no-op on fresh installs (column already exists from
-- the CREATE above) but lets older databases pick up the change without
-- a full re-init.
ALTER TABLE aiengine_schema.sessions
    ADD COLUMN IF NOT EXISTS parent_session_id UUID REFERENCES aiengine_schema.sessions(id) ON DELETE SET NULL;
ALTER TABLE aiengine_schema.sessions
    ADD COLUMN IF NOT EXISTS forked_from_message_id UUID;
-- Phase L: tool-catalogue mode for this session. "normal" or "deep_search".
-- Persisted so reloading an old deep-search chat keeps the right toolset.
ALTER TABLE aiengine_schema.sessions
    ADD COLUMN IF NOT EXISTS mode TEXT NOT NULL DEFAULT 'normal';

CREATE INDEX IF NOT EXISTS idx_sessions_tenant_user ON aiengine_schema.sessions(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_active ON aiengine_schema.sessions(tenant_id, archived_at)
    WHERE archived_at IS NULL;

ALTER TABLE aiengine_schema.sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE aiengine_schema.sessions FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON aiengine_schema.sessions
    USING (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid);

-- ─── messages ───────────────────────────────────────────────────────────
-- Append-only conversation log. `blocks` holds the ContentBlock array
-- (text, thinking, tool_use, tool_result) as JSONB.
CREATE TABLE IF NOT EXISTS aiengine_schema.messages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID NOT NULL REFERENCES aiengine_schema.sessions(id) ON DELETE CASCADE,
    tenant_id           UUID NOT NULL REFERENCES identity_schema.tenants(id) ON DELETE RESTRICT,
    role                TEXT NOT NULL,  -- system | user | assistant | tool
    blocks              JSONB NOT NULL,
    token_usage         JSONB,
    sequence_number     BIGINT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (session_id, sequence_number)
);

CREATE INDEX IF NOT EXISTS idx_messages_session ON aiengine_schema.messages(session_id, sequence_number);
CREATE INDEX IF NOT EXISTS idx_messages_tenant ON aiengine_schema.messages(tenant_id);

ALTER TABLE aiengine_schema.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE aiengine_schema.messages FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON aiengine_schema.messages
    USING (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid);

-- ─── tool_invocations ───────────────────────────────────────────────────
-- Audit log for every tool call. The PreToolUse hook writes the request,
-- the PostToolUse hook updates with the output and duration.
CREATE TABLE IF NOT EXISTS aiengine_schema.tool_invocations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID NOT NULL REFERENCES aiengine_schema.sessions(id) ON DELETE CASCADE,
    tenant_id           UUID NOT NULL REFERENCES identity_schema.tenants(id) ON DELETE RESTRICT,
    user_id             UUID NOT NULL,
    tool_name           TEXT NOT NULL,
    tool_input_hash     TEXT NOT NULL,
    tool_input          JSONB NOT NULL,
    tool_output_hash    TEXT,
    tool_output         TEXT,
    is_error            BOOLEAN NOT NULL DEFAULT FALSE,
    permission_mode     TEXT,
    duration_ms         INT,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_tools_session ON aiengine_schema.tool_invocations(session_id, started_at);
CREATE INDEX IF NOT EXISTS idx_tools_tenant ON aiengine_schema.tool_invocations(tenant_id, started_at);
CREATE INDEX IF NOT EXISTS idx_tools_name ON aiengine_schema.tool_invocations(tool_name);

ALTER TABLE aiengine_schema.tool_invocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE aiengine_schema.tool_invocations FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON aiengine_schema.tool_invocations
    USING (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid);

-- Identity schema tables.
-- Owned by Identity service. Tenants table is already created in 03-tenants-and-rls.sql.

-- ─── users ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS identity_schema.users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES identity_schema.tenants(id) ON DELETE RESTRICT,
    email           TEXT NOT NULL,
    display_name    TEXT NOT NULL DEFAULT '',
    password_hash   TEXT NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, email)
);

CREATE INDEX IF NOT EXISTS idx_users_tenant ON identity_schema.users(tenant_id);

ALTER TABLE identity_schema.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE identity_schema.users FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON identity_schema.users
    USING (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid);

-- ─── roles ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS identity_schema.roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES identity_schema.tenants(id) ON DELETE RESTRICT,
    name            TEXT NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_roles_tenant ON identity_schema.roles(tenant_id);

ALTER TABLE identity_schema.roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE identity_schema.roles FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON identity_schema.roles
    USING (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid);

-- ─── user_roles (junction) ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS identity_schema.user_roles (
    tenant_id   UUID NOT NULL REFERENCES identity_schema.tenants(id) ON DELETE RESTRICT,
    user_id     UUID NOT NULL REFERENCES identity_schema.users(id) ON DELETE CASCADE,
    role_id     UUID NOT NULL REFERENCES identity_schema.roles(id) ON DELETE CASCADE,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_user_roles_tenant ON identity_schema.user_roles(tenant_id);

ALTER TABLE identity_schema.user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE identity_schema.user_roles FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON identity_schema.user_roles
    USING (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid);

-- ─── refresh_tokens ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS identity_schema.refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES identity_schema.tenants(id) ON DELETE RESTRICT,
    user_id         UUID NOT NULL REFERENCES identity_schema.users(id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON identity_schema.refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_tenant ON identity_schema.refresh_tokens(tenant_id);
-- Refresh lookups happen by hash on every token rotation; without this
-- the path is a full scan as the table grows.
CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_tokens_token_hash
    ON identity_schema.refresh_tokens(token_hash);

ALTER TABLE identity_schema.refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE identity_schema.refresh_tokens FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON identity_schema.refresh_tokens
    USING (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid);

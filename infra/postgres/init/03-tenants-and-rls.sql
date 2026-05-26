-- Tenants table (owned by Identity) + global RLS pattern.
--
-- Every business table elsewhere will reference identity_schema.tenants(id)
-- and apply the same RLS policy. See: docs/architecture/002-shared-db-rls.md

-- ─── tenants table ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS identity_schema.tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    plan        TEXT NOT NULL DEFAULT 'free',     -- free | pro | enterprise
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─── How RLS gets configured per business table (template) ──────────────
-- For every tenant-scoped table:
--
--   ALTER TABLE <schema>.<table> ENABLE ROW LEVEL SECURITY;
--   ALTER TABLE <schema>.<table> FORCE  ROW LEVEL SECURITY;
--   CREATE POLICY tenant_isolation ON <schema>.<table>
--       USING (tenant_id = current_setting('app.current_tenant_id')::uuid)
--       WITH CHECK (tenant_id = current_setting('app.current_tenant_id')::uuid);
--
-- Each service calls `SET LOCAL app.current_tenant_id = '<uuid>'` at the
-- start of every transaction (or session-scope for pooled connections).
--
-- The platform_admin role can BYPASSRLS for migrations / admin tooling.

-- ─── A minimal seed tenant for dev (only on local) ──────────────────────
INSERT INTO identity_schema.tenants (id, name, plan)
VALUES ('00000000-0000-0000-0000-000000000001'::uuid, 'demo-tenant', 'pro')
ON CONFLICT (id) DO NOTHING;

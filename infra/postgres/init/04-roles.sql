-- Service-specific DB roles with least-privilege grants.
-- Each service connects with its own role; can only touch its own schema.

-- ─── Roles ──────────────────────────────────────────────────────────────
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'identity_user') THEN
        CREATE ROLE identity_user LOGIN PASSWORD 'identity_dev_pwd';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'documents_user') THEN
        CREATE ROLE documents_user LOGIN PASSWORD 'documents_dev_pwd';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'aiengine_user') THEN
        CREATE ROLE aiengine_user LOGIN PASSWORD 'aiengine_dev_pwd';
    END IF;
    -- Admin role for migrations + cross-schema reports — BYPASSRLS
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'platform_admin') THEN
        CREATE ROLE platform_admin LOGIN PASSWORD 'admin_dev_pwd' BYPASSRLS;
    END IF;
END $$;

-- ─── Grant per service ──────────────────────────────────────────────────
GRANT USAGE ON SCHEMA identity_schema TO identity_user;
GRANT ALL ON ALL TABLES IN SCHEMA identity_schema TO identity_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA identity_schema TO identity_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA identity_schema
    GRANT ALL ON TABLES TO identity_user;

GRANT USAGE ON SCHEMA documents_schema TO documents_user;
GRANT ALL ON ALL TABLES IN SCHEMA documents_schema TO documents_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA documents_schema TO documents_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA documents_schema
    GRANT ALL ON TABLES TO documents_user;

GRANT USAGE ON SCHEMA aiengine_schema TO aiengine_user;
GRANT ALL ON ALL TABLES IN SCHEMA aiengine_schema TO aiengine_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA aiengine_schema TO aiengine_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA aiengine_schema
    GRANT ALL ON TABLES TO aiengine_user;

-- Read-only access for AI Engine to identity.tenants (for tenant quota lookups)
GRANT USAGE ON SCHEMA identity_schema TO aiengine_user;
GRANT SELECT ON identity_schema.tenants TO aiengine_user;

-- Admin reaches everywhere
GRANT ALL ON ALL TABLES IN SCHEMA identity_schema  TO platform_admin;
GRANT ALL ON ALL TABLES IN SCHEMA documents_schema TO platform_admin;
GRANT ALL ON ALL TABLES IN SCHEMA aiengine_schema  TO platform_admin;

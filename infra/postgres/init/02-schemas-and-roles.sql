-- Service-scoped schemas + least-privilege application roles.
-- See ADR-002 — shared DB, schema-per-service, RLS for tenant isolation.

-- ─── Schemas ────────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS identity_schema;
CREATE SCHEMA IF NOT EXISTS documents_schema;
CREATE SCHEMA IF NOT EXISTS aiengine_schema;

-- ─── Application roles — least-privilege per service ────────────────────
-- These roles are intended for the application connections. The bootstrap
-- POSTGRES_USER (kai) remains the superuser for migrations / admin tasks.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'identity_user') THEN
        CREATE ROLE identity_user LOGIN PASSWORD 'identity_dev_pwd';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'documents_user') THEN
        CREATE ROLE documents_user LOGIN PASSWORD 'documents_dev_pwd';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'aiengine_user') THEN
        CREATE ROLE aiengine_user LOGIN PASSWORD 'aiengine_dev_pwd';
    END IF;
END$$;

-- Each user owns / has access to its OWN schema only.
GRANT USAGE  ON SCHEMA identity_schema  TO identity_user;
GRANT CREATE ON SCHEMA identity_schema  TO identity_user;

GRANT USAGE  ON SCHEMA documents_schema TO documents_user;
GRANT CREATE ON SCHEMA documents_schema TO documents_user;

GRANT USAGE  ON SCHEMA aiengine_schema  TO aiengine_user;
GRANT CREATE ON SCHEMA aiengine_schema  TO aiengine_user;

-- Read-only cross-schema access for limited use cases (tenant lookups).
-- Documents + AI Engine need to JOIN to tenants table for FK integrity.
GRANT USAGE ON SCHEMA identity_schema TO documents_user, aiengine_user;

-- Default privileges — future tables get the right grants automatically.
ALTER DEFAULT PRIVILEGES IN SCHEMA identity_schema
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO identity_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA documents_schema
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO documents_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA aiengine_schema
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO aiengine_user;

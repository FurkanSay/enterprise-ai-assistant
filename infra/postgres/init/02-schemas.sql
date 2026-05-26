-- Schema-per-service inside one database.
-- Each service connects with its own role; cross-schema reads are forbidden by default.
-- See: docs/architecture/002-shared-db-rls.md

CREATE SCHEMA IF NOT EXISTS identity_schema;
CREATE SCHEMA IF NOT EXISTS documents_schema;
CREATE SCHEMA IF NOT EXISTS aiengine_schema;

COMMENT ON SCHEMA identity_schema  IS 'Owned by Identity service (users, tenants, roles)';
COMMENT ON SCHEMA documents_schema IS 'Owned by Documents service (docs metadata, chunks meta)';
COMMENT ON SCHEMA aiengine_schema  IS 'Owned by AI Engine (sessions, messages, tool_invocations)';

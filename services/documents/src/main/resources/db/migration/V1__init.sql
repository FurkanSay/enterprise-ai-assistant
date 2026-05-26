-- Documents schema initial migration.
-- RLS isolation is mandatory for multi-tenancy — see ADR-002.

-- Note: `documents_schema` is created by Flyway (create-schemas: true).

CREATE TABLE documents_schema.documents (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    uploaded_by         UUID NOT NULL,
    title               TEXT NOT NULL,
    original_filename   TEXT NOT NULL,
    mime_type           TEXT NOT NULL,
    size_bytes          BIGINT NOT NULL,
    sha256              CHAR(64) NOT NULL,
    minio_object_key    TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL,
    chunk_count         INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_documents_tenant ON documents_schema.documents(tenant_id);
CREATE INDEX idx_documents_status ON documents_schema.documents(status);

-- Row-Level Security — see ADR-002. Application sets app.current_tenant_id
-- on every connection; Postgres auto-filters every row.
ALTER TABLE documents_schema.documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents_schema.documents FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON documents_schema.documents
    USING (tenant_id::text = current_setting('app.current_tenant_id', true));

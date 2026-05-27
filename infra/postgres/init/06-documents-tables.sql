-- Documents schema tables.
-- Owned by Documents service. Stores metadata; the file bytes live in MinIO.

CREATE TABLE IF NOT EXISTS documents_schema.documents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES identity_schema.tenants(id) ON DELETE RESTRICT,
    uploader_user_id    UUID NOT NULL,  -- references identity_schema.users.id (no cross-schema FK)
    title               TEXT NOT NULL,
    original_filename   TEXT NOT NULL,
    mime_type           TEXT NOT NULL,
    size_bytes          BIGINT NOT NULL,
    sha256              TEXT NOT NULL,
    -- DocumentStatus enum: UPLOADED | PARSING | CHUNKING | EMBEDDING | READY | FAILED
    status              TEXT NOT NULL DEFAULT 'UPLOADED',
    minio_object_key    TEXT NOT NULL,
    chunk_count         INT NOT NULL DEFAULT 0,
    failure_reason      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_documents_tenant ON documents_schema.documents(tenant_id);
CREATE INDEX IF NOT EXISTS idx_documents_status ON documents_schema.documents(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_documents_sha256 ON documents_schema.documents(tenant_id, sha256);

-- Phase L: lineage for papers ingested via Deep Search. NULL on
-- documents uploaded the normal way through the /documents UI.
ALTER TABLE documents_schema.documents ADD COLUMN IF NOT EXISTS source_session_id UUID;
ALTER TABLE documents_schema.documents ADD COLUMN IF NOT EXISTS source_paper_doi TEXT;
ALTER TABLE documents_schema.documents ADD COLUMN IF NOT EXISTS source_paper_title TEXT;
CREATE INDEX IF NOT EXISTS idx_documents_source_session
    ON documents_schema.documents(tenant_id, source_session_id)
    WHERE source_session_id IS NOT NULL;

ALTER TABLE documents_schema.documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents_schema.documents FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON documents_schema.documents
    USING (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid);

-- ─── document_chunks_meta ───────────────────────────────────────────────
-- Lightweight metadata about chunks. The chunk text + vectors live in
-- Qdrant and tantivy (Processing service). This table is for auditing and
-- statistics, not for serving chunks back to the LLM.
CREATE TABLE IF NOT EXISTS documents_schema.document_chunks_meta (
    chunk_id            UUID PRIMARY KEY,
    document_id         UUID NOT NULL REFERENCES documents_schema.documents(id) ON DELETE CASCADE,
    tenant_id           UUID NOT NULL REFERENCES identity_schema.tenants(id) ON DELETE RESTRICT,
    sequence            INT NOT NULL,
    token_count         INT NOT NULL DEFAULT 0,
    source_location     TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chunks_doc ON documents_schema.document_chunks_meta(document_id);
CREATE INDEX IF NOT EXISTS idx_chunks_tenant ON documents_schema.document_chunks_meta(tenant_id);

ALTER TABLE documents_schema.document_chunks_meta ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents_schema.document_chunks_meta FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON documents_schema.document_chunks_meta
    USING (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', TRUE)::uuid);

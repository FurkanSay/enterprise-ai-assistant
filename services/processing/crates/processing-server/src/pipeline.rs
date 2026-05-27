//! Orchestrates the full per-event pipeline.
//!
//!   doc.uploaded.v1
//!         │
//!         ▼
//!   PARSING ─► download text.txt from MinIO
//!         │
//!         ▼
//!   CHUNKING ─► chunker::chunk_text
//!         │
//!         ▼
//!   EMBEDDING ─► fastembed batch encode  ─► Qdrant upsert + tantivy index
//!         │
//!         ▼
//!   READY ─► chunks_meta rows + status update (single txn)
//!
//! On any error, the document is marked FAILED with the error string. The
//! Redis Streams entry is NOT ACKed by the consumer in that case, so a
//! restart will reprocess the same event — idempotent because chunks_meta
//! INSERTs ON CONFLICT DO NOTHING and Qdrant upserts by chunk_id.

use std::sync::Arc;

use anyhow::{Context, Result};
use tracing::{info, warn};

use crate::db::Db;
use crate::embedder::Embedder;
use crate::event::DocUploadedEvent;
use crate::storage::ObjectStore;
use crate::vector_store::VectorStore;
use bm25_index::Bm25Index;
use chunker::{chunk_text, ChunkConfig};

#[derive(Clone)]
pub struct Pipeline {
    storage: Arc<ObjectStore>,
    embedder: Embedder,
    qdrant: VectorStore,
    bm25: Arc<Bm25Index>,
    db: Db,
}

pub struct PipelineStats {
    pub chunk_count: usize,
}

impl Pipeline {
    pub fn new(
        storage: ObjectStore,
        embedder: Embedder,
        qdrant: VectorStore,
        bm25: Arc<Bm25Index>,
        db: Db,
    ) -> Self {
        Self {
            storage: Arc::new(storage),
            embedder,
            qdrant,
            bm25,
            db,
        }
    }

    pub async fn process(&self, event: &DocUploadedEvent) -> Result<PipelineStats> {
        match self.process_inner(event).await {
            Ok(stats) => Ok(stats),
            Err(e) => {
                // Best-effort: try to mark the document failed. If that also
                // fails, surface the original error so it stays in logs.
                let msg = format!("{e}");
                if let Err(mark) = self
                    .db
                    .mark_failed(event.tenant_id, event.document_id, &msg)
                    .await
                {
                    warn!(
                        document_id = %event.document_id,
                        error = %mark,
                        "pipeline.mark_failed.failed"
                    );
                }
                Err(e)
            }
        }
    }

    async fn process_inner(&self, event: &DocUploadedEvent) -> Result<PipelineStats> {
        // 1. PARSING — download the Tika-extracted text.txt sidecar.
        self.db
            .mark_status(event.tenant_id, event.document_id, "PARSING")
            .await
            .context("status -> PARSING")?;
        let text = self
            .storage
            .get_text(&event.text_object_key)
            .await
            .context("download text.txt")?;
        info!(
            document_id = %event.document_id,
            chars = text.len(),
            "pipeline.text.downloaded"
        );

        // 2. CHUNKING — pure CPU, no awaits.
        self.db
            .mark_status(event.tenant_id, event.document_id, "CHUNKING")
            .await?;
        let chunks = chunk_text(&text, ChunkConfig::default());
        if chunks.is_empty() {
            anyhow::bail!("chunker produced no chunks — empty or unreadable text");
        }
        info!(
            document_id = %event.document_id,
            chunks = chunks.len(),
            "pipeline.chunked"
        );

        // 3. EMBEDDING — batch encode all chunk texts in one fastembed call.
        self.db
            .mark_status(event.tenant_id, event.document_id, "EMBEDDING")
            .await?;
        let texts: Vec<String> = chunks.iter().map(|c| c.text.clone()).collect();
        let vectors = self.embedder.embed(texts).await.context("embed")?;
        info!(
            document_id = %event.document_id,
            vectors = vectors.len(),
            dims = self.embedder.dimensions,
            "pipeline.embedded"
        );

        // 4. Dual index write — Qdrant (semantic) + tantivy (lexical).
        self.qdrant
            .upsert(event.tenant_id, event.document_id, &chunks, &vectors)
            .await
            .context("qdrant upsert")?;

        let bm25 = self.bm25.clone();
        let tenant_str = event.tenant_id.to_string();
        let doc_str = event.document_id.to_string();
        let chunks_for_bm25 = chunks.clone();
        tokio::task::spawn_blocking(move || {
            bm25.index_document(&tenant_str, &doc_str, &chunks_for_bm25)
        })
        .await
        .context("bm25 spawn_blocking join")?
        .context("bm25 index_document")?;

        // 5. READY — chunks_meta + status flip in one txn.
        self.db
            .finalize_ready(event.tenant_id, event.document_id, &chunks)
            .await
            .context("finalize ready")?;

        Ok(PipelineStats {
            chunk_count: chunks.len(),
        })
    }
}

//! BM25 inverted index — public types and stubs.
//!
//! The real tantivy-backed implementation lives in Phase E. For Phase B5
//! the crate only needs to compile and expose the request/response shapes
//! the gRPC handlers will return; the gateway, AI Engine and Documents
//! services already depend on this crate via the workspace graph.

use serde::{Deserialize, Serialize};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum IndexError {
    #[error("tantivy error: {0}")]
    Tantivy(String),
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
}

/// One scored chunk returned by `Bm25Index::search`.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BM25Hit {
    pub chunk_id: String,
    pub document_id: String,
    pub score: f32,
    pub text: String,
    pub sequence: u32,
    pub source_location: String,
}

/// Lightweight handle to a tantivy-backed index.
///
/// The struct keeps an internal path string for Phase E to consume; the
/// actual `tantivy::Index` is constructed there. Keeping the surface small
/// here avoids dragging the multi-error-type API into Phase B5.
pub struct Bm25Index {
    _path: String,
}

impl Bm25Index {
    /// Open or create an index rooted at `path`.
    pub fn open_or_create(path: impl Into<String>) -> Result<Self, IndexError> {
        Ok(Self { _path: path.into() })
    }

    /// Bulk-index chunks for a single document.
    /// TODO (Phase E): write tantivy documents with tenant_id field.
    pub fn index_document(
        &self,
        _tenant_id: &str,
        _document_id: &str,
        _chunks: &[chunker::Chunk],
    ) -> Result<usize, IndexError> {
        Ok(0)
    }

    /// Tenant-scoped BM25 search.
    /// TODO (Phase E): parse query, AND with tenant_id term, return top_k.
    pub fn search(
        &self,
        _tenant_id: &str,
        _query: &str,
        _top_k: usize,
    ) -> Result<Vec<BM25Hit>, IndexError> {
        Ok(Vec::new())
    }
}

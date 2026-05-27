//! Tantivy-backed BM25 inverted index, tenant-scoped.
//!
//! Schema:
//!   - chunk_id          STRING (stored, indexed)
//!   - document_id       STRING (stored, indexed)
//!   - tenant_id         STRING (stored, indexed)        ← every search ANDs this
//!   - sequence          U64    (stored, fast)
//!   - text              TEXT   (stored, indexed)        ← BM25 scoring target
//!   - source_location   STRING (stored)
//!
//! Tenant isolation is enforced by ANDing a TermQuery on tenant_id with the
//! user's query. There is no equivalent of Postgres FORCE ROW LEVEL SECURITY
//! in tantivy, so the caller MUST go through this crate's `search()` rather
//! than building queries themselves.

use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use tantivy::collector::TopDocs;
use tantivy::query::{BooleanQuery, Occur, Query, QueryParser, TermQuery};
use tantivy::schema::{Field, IndexRecordOption, Schema, STORED, STRING, TEXT};
use tantivy::{doc, Index, IndexReader, IndexWriter, ReloadPolicy, Term, TantivyDocument};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum IndexError {
    #[error("tantivy error: {0}")]
    Tantivy(String),
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("query parse error: {0}")]
    Query(String),
}

impl From<tantivy::TantivyError> for IndexError {
    fn from(value: tantivy::TantivyError) -> Self {
        IndexError::Tantivy(value.to_string())
    }
}

/// One scored chunk returned by `Bm25Index::search`.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BM25Hit {
    pub chunk_id: String,
    pub document_id: String,
    pub score: f32,
    pub text: String,
    pub sequence: u64,
    pub source_location: String,
}

struct Fields {
    chunk_id: Field,
    document_id: Field,
    tenant_id: Field,
    sequence: Field,
    text: Field,
    source_location: Field,
}

/// Open/create a tantivy index at `path`. Single index, all tenants, with
/// tenant_id as a mandatory filter field. (Per-tenant index directories
/// would be cleaner isolation but explode the file-handle count.)
pub struct Bm25Index {
    index: Index,
    reader: IndexReader,
    writer: std::sync::Mutex<IndexWriter>,
    fields: Fields,
}

impl Bm25Index {
    pub fn open_or_create(path: impl AsRef<Path>) -> Result<Self, IndexError> {
        let path: PathBuf = path.as_ref().to_path_buf();
        std::fs::create_dir_all(&path)?;

        let mut builder = Schema::builder();
        let chunk_id = builder.add_text_field("chunk_id", STRING | STORED);
        let document_id = builder.add_text_field("document_id", STRING | STORED);
        let tenant_id = builder.add_text_field("tenant_id", STRING | STORED);
        let sequence = builder.add_u64_field("sequence", STORED);
        let text = builder.add_text_field("text", TEXT | STORED);
        let source_location = builder.add_text_field("source_location", STRING | STORED);
        let schema = builder.build();

        // MmapDirectory::open returns its own OpenDirectoryError, which
        // does not auto-convert into our IndexError. Map it explicitly so
        // the rest of the file can keep using `?` on the tantivy paths.
        let dir = tantivy::directory::MmapDirectory::open(&path)
            .map_err(|e| IndexError::Tantivy(e.to_string()))?;
        let index = Index::open_or_create(dir, schema)?;
        // 50MB heap for the writer's in-memory segment buffer. The writer
        // commits to disk when this fills up, or on explicit commit().
        let writer = index.writer(50_000_000)?;
        let reader = index
            .reader_builder()
            .reload_policy(ReloadPolicy::OnCommitWithDelay)
            .try_into()?;

        Ok(Self {
            index,
            reader,
            writer: std::sync::Mutex::new(writer),
            fields: Fields {
                chunk_id,
                document_id,
                tenant_id,
                sequence,
                text,
                source_location,
            },
        })
    }

    /// Drop every indexed chunk belonging to (tenant_id, document_id).
    /// Tantivy doesn't have AND-delete in one call, so we delete by
    /// document_id and rely on UUIDs being globally unique — the
    /// tenant filter is enforced at search time too.
    pub fn delete_document(
        &self,
        _tenant_id: &str,
        document_id: &str,
    ) -> Result<(), IndexError> {
        let mut writer = self
            .writer
            .lock()
            .map_err(|e| IndexError::Tantivy(format!("writer mutex poisoned: {e}")))?;
        let term = Term::from_field_text(self.fields.document_id, document_id);
        writer.delete_term(term);
        writer.commit()?;
        Ok(())
    }

    /// Bulk-index chunks for a single document.
    /// Returns the number of chunks indexed.
    pub fn index_document(
        &self,
        tenant_id: &str,
        document_id: &str,
        chunks: &[chunker::Chunk],
    ) -> Result<usize, IndexError> {
        let mut writer = self
            .writer
            .lock()
            .map_err(|e| IndexError::Tantivy(format!("writer mutex poisoned: {e}")))?;

        for chunk in chunks {
            writer.add_document(doc!(
                self.fields.chunk_id => chunk.chunk_id.as_str(),
                self.fields.document_id => document_id,
                self.fields.tenant_id => tenant_id,
                self.fields.sequence => chunk.sequence as u64,
                self.fields.text => chunk.text.as_str(),
                self.fields.source_location => chunk.source_location.as_str(),
            ))?;
        }
        writer.commit()?;
        Ok(chunks.len())
    }

    /// Tenant-scoped BM25 search. The tenant_id filter is ANDed with the
    /// user query — there is no way to search across tenants through this
    /// API, by design.
    pub fn search(
        &self,
        tenant_id: &str,
        query: &str,
        top_k: usize,
    ) -> Result<Vec<BM25Hit>, IndexError> {
        let searcher = self.reader.searcher();

        let user_query = QueryParser::for_index(&self.index, vec![self.fields.text])
            .parse_query(query)
            .map_err(|e| IndexError::Query(e.to_string()))?;

        let tenant_term = Term::from_field_text(self.fields.tenant_id, tenant_id);
        let tenant_query = TermQuery::new(tenant_term, IndexRecordOption::Basic);

        let combined: Box<dyn Query> = Box::new(BooleanQuery::new(vec![
            (Occur::Must, Box::new(tenant_query)),
            (Occur::Must, user_query),
        ]));

        let top = searcher.search(combined.as_ref(), &TopDocs::with_limit(top_k))?;
        let mut hits = Vec::with_capacity(top.len());
        for (score, addr) in top {
            let doc: TantivyDocument = searcher.doc(addr)?;
            // Safety belt: if the AND-filter ever leaks (custom analyzers,
            // index corruption), refuse to surface cross-tenant hits.
            if let Some(stored) = read_text(&doc, self.fields.tenant_id) {
                if stored != tenant_id {
                    return Err(IndexError::Tantivy(format!(
                        "tenant mismatch: stored={stored} expected={tenant_id}"
                    )));
                }
            }
            hits.push(BM25Hit {
                chunk_id: read_text(&doc, self.fields.chunk_id).unwrap_or_default(),
                document_id: read_text(&doc, self.fields.document_id).unwrap_or_default(),
                score,
                text: read_text(&doc, self.fields.text).unwrap_or_default(),
                sequence: read_u64(&doc, self.fields.sequence).unwrap_or_default(),
                source_location: read_text(&doc, self.fields.source_location)
                    .unwrap_or_default(),
            });
        }
        Ok(hits)
    }
}

fn read_text(doc: &TantivyDocument, field: Field) -> Option<String> {
    use tantivy::schema::Value;
    doc.get_first(field)
        .and_then(|v| v.as_str().map(|s| s.to_string()))
}

fn read_u64(doc: &TantivyDocument, field: Field) -> Option<u64> {
    use tantivy::schema::Value;
    doc.get_first(field).and_then(|v| v.as_u64())
}

#[cfg(test)]
mod tests {
    use super::*;
    use chunker::Chunk;

    fn mk_chunks() -> Vec<Chunk> {
        vec![
            Chunk {
                chunk_id: "c1".into(),
                sequence: 0,
                text: "rust async tokio".into(),
                source_location: "p1".into(),
            },
            Chunk {
                chunk_id: "c2".into(),
                sequence: 1,
                text: "java spring boot tomcat".into(),
                source_location: "p2".into(),
            },
        ]
    }

    #[test]
    fn search_returns_relevant_hit() {
        let tmp = tempdir_path("bm25-search");
        let idx = Bm25Index::open_or_create(&tmp).unwrap();
        idx.index_document("tenant-a", "doc-1", &mk_chunks()).unwrap();
        let hits = idx.search("tenant-a", "tokio", 5).unwrap();
        assert!(!hits.is_empty());
        assert_eq!(hits[0].chunk_id, "c1");
    }

    #[test]
    fn tenant_isolation_holds() {
        let tmp = tempdir_path("bm25-tenant");
        let idx = Bm25Index::open_or_create(&tmp).unwrap();
        idx.index_document("tenant-a", "doc-1", &mk_chunks()).unwrap();
        let hits = idx.search("tenant-b", "tokio", 5).unwrap();
        assert!(hits.is_empty());
    }

    fn tempdir_path(name: &str) -> std::path::PathBuf {
        let mut p = std::env::temp_dir();
        p.push(format!(
            "kai-bm25-{}-{}",
            name,
            uuid::Uuid::new_v4().simple()
        ));
        p
    }
}

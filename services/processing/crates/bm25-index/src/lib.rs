//! BM25 inverted index wrapper around tantivy.
//!
//! Multi-tenant: tenant_id is a stored AND indexed field. Every query MUST
//! include a tenant_id filter — enforced by [`Bm25Index::search`].

use std::path::Path;

use chunker::Chunk;
use serde::{Deserialize, Serialize};
use tantivy::{
    collector::TopDocs,
    query::{BooleanQuery, Occur, Query, QueryParser, TermQuery},
    schema::{Field, IndexRecordOption, Schema, STORED, STRING, TEXT},
    Index, IndexWriter, ReloadPolicy, Term, TantivyDocument,
};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum IndexError {
    #[error("tantivy error: {0}")]
    Tantivy(#[from] tantivy::TantivyError),
    #[error("query parse: {0}")]
    QueryParse(#[from] tantivy::query::QueryParserError),
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BM25Hit {
    pub chunk_id: String,
    pub document_id: String,
    pub score: f32,
    pub text: String,
    pub sequence: u32,
    pub source_location: String,
}

pub struct Bm25Index {
    index: Index,
    schema: IndexSchema,
}

struct IndexSchema {
    chunk_id: Field,
    document_id: Field,
    tenant_id: Field,
    text: Field,
    sequence: Field,
    source_location: Field,
}

impl Bm25Index {
    pub fn open_or_create(path: impl AsRef<Path>) -> Result<Self, IndexError> {
        let path = path.as_ref();
        std::fs::create_dir_all(path).ok();

        let mut builder = Schema::builder();
        let chunk_id = builder.add_text_field("chunk_id", STRING | STORED);
        let document_id = builder.add_text_field("document_id", STRING | STORED);
        let tenant_id = builder.add_text_field("tenant_id", STRING);
        let text = builder.add_text_field("text", TEXT | STORED);
        let sequence = builder.add_u64_field("sequence", STORED);
        let source_location = builder.add_text_field("source_location", STORED);
        let schema = builder.build();

        let index = Index::open_or_create(tantivy::directory::MmapDirectory::open(path)?, schema)?;

        Ok(Self {
            index,
            schema: IndexSchema {
                chunk_id,
                document_id,
                tenant_id,
                text,
                sequence,
                source_location,
            },
        })
    }

    /// Bulk-index chunks for a single document.
    pub fn index_document(
        &self,
        tenant_id: &str,
        document_id: &str,
        chunks: &[Chunk],
    ) -> Result<usize, IndexError> {
        let mut writer: IndexWriter = self.index.writer(50_000_000)?;
        let s = &self.schema;
        for chunk in chunks {
            let mut doc = TantivyDocument::default();
            doc.add_text(s.chunk_id, &chunk.chunk_id);
            doc.add_text(s.document_id, document_id);
            doc.add_text(s.tenant_id, tenant_id);
            doc.add_text(s.text, &chunk.text);
            doc.add_u64(s.sequence, u64::from(chunk.sequence));
            doc.add_text(s.source_location, &chunk.source_location);
            writer.add_document(doc)?;
        }
        writer.commit()?;
        Ok(chunks.len())
    }

    /// Tenant-scoped BM25 search.
    pub fn search(
        &self,
        tenant_id: &str,
        query: &str,
        top_k: usize,
    ) -> Result<Vec<BM25Hit>, IndexError> {
        let reader = self
            .index
            .reader_builder()
            .reload_policy(ReloadPolicy::OnCommitWithDelay)
            .try_into()?;
        let searcher = reader.searcher();
        let s = &self.schema;

        let parser = QueryParser::for_index(&self.index, vec![s.text]);
        let text_query: Box<dyn Query> = Box::new(parser.parse_query(query)?);
        let tenant_query: Box<dyn Query> = Box::new(TermQuery::new(
            Term::from_field_text(s.tenant_id, tenant_id),
            IndexRecordOption::Basic,
        ));

        let combined = BooleanQuery::new(vec![
            (Occur::Must, text_query),
            (Occur::Must, tenant_query),
        ]);

        let top = searcher.search(&combined, &TopDocs::with_limit(top_k))?;

        let mut hits = Vec::with_capacity(top.len());
        for (score, addr) in top {
            let doc: TantivyDocument = searcher.doc(addr)?;
            hits.push(BM25Hit {
                chunk_id: read_str(&doc, s.chunk_id).unwrap_or_default(),
                document_id: read_str(&doc, s.document_id).unwrap_or_default(),
                score,
                text: read_str(&doc, s.text).unwrap_or_default(),
                sequence: read_u64(&doc, s.sequence).unwrap_or(0) as u32,
                source_location: read_str(&doc, s.source_location).unwrap_or_default(),
            });
        }
        Ok(hits)
    }
}

fn read_str(doc: &TantivyDocument, field: Field) -> Option<String> {
    use tantivy::schema::Value;
    doc.get_first(field).and_then(|v| v.as_str().map(str::to_owned))
}

fn read_u64(doc: &TantivyDocument, field: Field) -> Option<u64> {
    use tantivy::schema::Value;
    doc.get_first(field).and_then(|v| v.as_u64())
}

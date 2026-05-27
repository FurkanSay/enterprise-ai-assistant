//! Qdrant writer. Single collection `documents`; every point's payload has
//! `tenant_id`, `document_id`, `chunk_id`, `sequence`, `text`,
//! `source_location`. Tenant isolation is enforced by the search-side
//! filter — callers MUST pass a `must` clause with `tenant_id`.
//!
//! Collection bootstrap: created lazily on first connect with the
//! embedder's dimensionality. We do not recreate if it already exists with
//! a different vector size — that would silently drop data. Operators must
//! delete the collection by hand to change the model.

use anyhow::{Context, Result};
use qdrant_client::qdrant::{
    point_id::PointIdOptions, value::Kind as ValueKind, Condition, CreateCollectionBuilder,
    DeletePointsBuilder, Distance, Filter, PointStruct, UpsertPointsBuilder, Value,
    VectorParamsBuilder,
};
use qdrant_client::Qdrant;
use std::collections::HashMap;
use tracing::info;
use uuid::Uuid;

use crate::config::Config;

#[derive(Clone)]
pub struct VectorStore {
    client: std::sync::Arc<Qdrant>,
    collection: String,
}

impl VectorStore {
    pub async fn connect(cfg: &Config, dimensions: u64) -> Result<Self> {
        let client = Qdrant::from_url(&cfg.qdrant_url)
            .build()
            .context("qdrant client build")?;

        let collections = client.list_collections().await.context("list_collections")?;
        let exists = collections
            .collections
            .iter()
            .any(|c| c.name == cfg.qdrant_collection);

        if !exists {
            info!(
                collection = %cfg.qdrant_collection,
                dim = dimensions,
                "qdrant.collection.creating"
            );
            client
                .create_collection(
                    CreateCollectionBuilder::new(&cfg.qdrant_collection)
                        .vectors_config(VectorParamsBuilder::new(dimensions, Distance::Cosine)),
                )
                .await
                .context("qdrant create_collection")?;
        }

        Ok(Self {
            client: std::sync::Arc::new(client),
            collection: cfg.qdrant_collection.clone(),
        })
    }

    pub async fn upsert(
        &self,
        tenant_id: Uuid,
        document_id: Uuid,
        chunks: &[chunker::Chunk],
        vectors: &[Vec<f32>],
    ) -> Result<()> {
        assert_eq!(chunks.len(), vectors.len(), "chunks/vectors length mismatch");

        let mut points: Vec<PointStruct> = Vec::with_capacity(chunks.len());
        for (chunk, vector) in chunks.iter().zip(vectors.iter()) {
            let payload: HashMap<String, Value> = HashMap::from([
                ("tenant_id".to_string(), str_value(&tenant_id.to_string())),
                ("document_id".to_string(), str_value(&document_id.to_string())),
                ("chunk_id".to_string(), str_value(&chunk.chunk_id)),
                ("sequence".to_string(), int_value(chunk.sequence as i64)),
                ("text".to_string(), str_value(&chunk.text)),
                ("source_location".to_string(), str_value(&chunk.source_location)),
            ]);
            // Qdrant requires UUID or unsigned integer point IDs.
            let point_id = qdrant_client::qdrant::PointId {
                point_id_options: Some(PointIdOptions::Uuid(chunk.chunk_id.clone())),
            };
            points.push(PointStruct {
                id: Some(point_id),
                vectors: Some(vector.clone().into()),
                payload,
            });
        }

        self.client
            .upsert_points(UpsertPointsBuilder::new(&self.collection, points).wait(true))
            .await
            .context("qdrant upsert_points")?;
        Ok(())
    }

    /// Delete every point belonging to (tenant_id, document_id). The
    /// tenant condition is redundant given UUIDs are unique, but we
    /// keep it as a defense-in-depth match: even if a future schema
    /// bug let two tenants share a document_id, we'd only touch one
    /// tenant's points here.
    pub async fn delete_document(
        &self,
        tenant_id: Uuid,
        document_id: Uuid,
    ) -> Result<()> {
        let filter = Filter::must(vec![
            Condition::matches("tenant_id", tenant_id.to_string()),
            Condition::matches("document_id", document_id.to_string()),
        ]);
        self.client
            .delete_points(
                DeletePointsBuilder::new(&self.collection)
                    .points(filter)
                    .wait(true),
            )
            .await
            .context("qdrant delete_points")?;
        Ok(())
    }
}

fn str_value(s: &str) -> Value {
    Value {
        kind: Some(ValueKind::StringValue(s.to_string())),
    }
}

fn int_value(i: i64) -> Value {
    Value {
        kind: Some(ValueKind::IntegerValue(i)),
    }
}

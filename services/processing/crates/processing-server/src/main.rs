//! Processing service — Phase E.
//!
//! Boot sequence:
//!   1. Load config from env.
//!   2. Connect to Redis, MinIO, Qdrant, Postgres.
//!   3. Open the tantivy index on the local volume.
//!   4. Load fastembed (downloads the model on first boot, then cached).
//!   5. Spawn the Redis Streams consumer loop.
//!   6. Serve /health/live + /health/ready on the HTTP port.
//!
//! Health gating: /health/ready returns 503 until the consumer has produced
//! a successful poll. That way docker-compose `depends_on: condition: service_healthy`
//! actually means something — Documents will not get traffic before
//! Processing is consuming events.

use std::net::SocketAddr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

use anyhow::{Context, Result};
use axum::{
    extract::State,
    http::StatusCode,
    routing::{get, post},
    Json, Router,
};
use serde::{Deserialize, Serialize};
use serde_json::json;
use tracing::{error, info};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

mod config;
mod consumer;
mod db;
mod embedder;
mod event;
mod pipeline;
mod storage;
mod vector_store;

use crate::config::Config;
use crate::consumer::Consumer;
use crate::db::Db;
use crate::embedder::Embedder;
use crate::pipeline::Pipeline;
use crate::storage::ObjectStore;
use crate::vector_store::VectorStore;
use bm25_index::Bm25Index;

#[derive(Clone)]
struct AppState {
    ready: Arc<AtomicBool>,
    /// Shared with the consumer; we expose the same model on /embed so
    /// query-time embeddings use the EXACT same vectors as ingest-time.
    /// Single source of truth — if we rotate the model tomorrow, both
    /// paths move together. No silent retrieval drift.
    embedder: crate::embedder::Embedder,
}

#[derive(Deserialize)]
struct EmbedRequest {
    text: String,
}

#[derive(Serialize)]
struct EmbedResponse {
    vector: Vec<f32>,
    dimensions: u64,
}

#[tokio::main]
async fn main() -> Result<()> {
    init_tracing();
    info!(service = "processing", "starting");

    let cfg = Config::from_env().context("load config")?;
    info!(redis = %cfg.redis_url, qdrant = %cfg.qdrant_url, bucket = %cfg.minio_bucket, "config.loaded");

    // Each dependency is fetched up-front. We want the container to fail
    // fast on misconfiguration rather than half-start.
    let storage = ObjectStore::connect(&cfg).await.context("storage connect")?;
    let embedder = Embedder::load(&cfg).context("embedder load")?;
    let qdrant = VectorStore::connect(&cfg, embedder.dimensions)
        .await
        .context("qdrant connect")?;
    let db = Db::connect(&cfg).await.context("db connect")?;
    let bm25 = Arc::new(Bm25Index::open_or_create(&cfg.bm25_index_path).context("bm25 open")?);

    // Clone the embedder once: one copy keeps living inside the pipeline
    // for ingest, the other is exposed on /embed for query-side reuse.
    // `Embedder` wraps Arc<TextEmbedding>, so this clone is cheap.
    let embedder_for_http = embedder.clone();
    let pipeline = Pipeline::new(storage, embedder, qdrant, bm25, db);

    let ready = Arc::new(AtomicBool::new(false));
    let state = AppState {
        ready: ready.clone(),
        embedder: embedder_for_http,
    };

    // Consumer task — owns the Redis connection for its lifetime.
    let consumer = Consumer::connect(&cfg).await.context("consumer connect")?;
    ready.store(true, Ordering::SeqCst);
    tokio::spawn(async move {
        if let Err(e) = consumer.run(pipeline).await {
            error!(error = %e, "consumer.run.exited");
        }
    });

    // HTTP surface.
    let app = Router::new()
        .route(
            "/health/live",
            get(|| async { Json(json!({"status":"ok","service":"processing"})) }),
        )
        .route("/health/ready", get(ready_handler))
        .route("/embed", post(embed_handler))
        .with_state(state);

    let addr: SocketAddr = cfg.http_addr.parse().context("http_addr parse")?;
    info!(%addr, "listening");
    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await?;
    Ok(())
}

async fn ready_handler(State(state): State<AppState>) -> (StatusCode, Json<serde_json::Value>) {
    if state.ready.load(Ordering::SeqCst) {
        (StatusCode::OK, Json(json!({"status":"ok"})))
    } else {
        (StatusCode::SERVICE_UNAVAILABLE, Json(json!({"status":"starting"})))
    }
}

/// Query-time embedding endpoint. AI Engine POSTs the user's question
/// here and gets back a vector compatible with what we wrote at ingest.
/// Internal-only — no auth (this lives on the docker network).
async fn embed_handler(
    State(state): State<AppState>,
    Json(req): Json<EmbedRequest>,
) -> Result<Json<EmbedResponse>, (StatusCode, String)> {
    if req.text.trim().is_empty() {
        return Err((StatusCode::BAD_REQUEST, "text is required".to_string()));
    }
    let vectors = state
        .embedder
        .embed(vec![req.text])
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, format!("embed: {e:#}")))?;
    let vector = vectors
        .into_iter()
        .next()
        .ok_or((StatusCode::INTERNAL_SERVER_ERROR, "empty embed result".to_string()))?;
    let dimensions = vector.len() as u64;
    Ok(Json(EmbedResponse { vector, dimensions }))
}

fn init_tracing() {
    let env_filter = EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info"));
    tracing_subscriber::registry()
        .with(env_filter)
        .with(tracing_subscriber::fmt::layer().json())
        .init();
}

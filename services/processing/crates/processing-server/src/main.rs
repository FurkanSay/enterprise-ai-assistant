//! Processing server entry point.
//!
//! Two concurrent surfaces:
//!   1. HTTP /health endpoints (axum, port 8083)
//!   2. gRPC ProcessingService (tonic — see grpc.rs)
//!      Currently embedded into the same port; can split if needed.
//!
//! Plus: a Redis Streams consumer task that picks up `doc.uploaded` events
//! and triggers chunking + BM25 indexing.

mod config;
mod consumer;
mod grpc;
mod health;
mod telemetry;

use std::sync::Arc;

use anyhow::Result;
use tracing::info;

#[tokio::main]
async fn main() -> Result<()> {
    let cfg = config::Config::from_env()?;
    telemetry::init(&cfg)?;

    info!(
        service = "processing",
        version = env!("CARGO_PKG_VERSION"),
        "starting"
    );

    // Shared BM25 index — wrap once, share across consumer + gRPC.
    let index = Arc::new(bm25_index::Bm25Index::open_or_create(&cfg.index_path)?);

    // Redis consumer (background)
    let consumer_handle = tokio::spawn(consumer::run_doc_uploaded_consumer(
        cfg.clone(),
        index.clone(),
    ));

    // HTTP + gRPC server
    let server_handle = tokio::spawn(grpc::serve(cfg, index));

    tokio::select! {
        res = consumer_handle => {
            tracing::error!(?res, "consumer task exited");
        }
        res = server_handle => {
            tracing::error!(?res, "server task exited");
        }
    }

    telemetry::shutdown();
    Ok(())
}

//! Processing service entry point — Phase B5 minimal shell.
//!
//! Right now this only serves `/health/live` and `/health/ready` so the
//! container can stand up under docker compose. The real surface
//! (gRPC ProcessingService, Redis Streams consumer, OpenTelemetry pipeline)
//! lands in Phase E once tantivy indexing is wired up.

use std::net::SocketAddr;

use anyhow::Result;
use axum::{routing::get, Json, Router};
use serde_json::json;
use tracing::info;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

#[tokio::main]
async fn main() -> Result<()> {
    init_tracing();
    info!(service = "processing", "starting");

    let app = Router::new()
        .route("/health/live", get(|| async { Json(json!({"status":"ok","service":"processing"})) }))
        .route("/health/ready", get(|| async { Json(json!({"status":"ok","service":"processing"})) }));

    let addr: SocketAddr = "0.0.0.0:8083".parse()?;
    info!(%addr, "listening");
    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await?;
    Ok(())
}

fn init_tracing() {
    let env_filter = EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info"));
    tracing_subscriber::registry()
        .with(env_filter)
        .with(tracing_subscriber::fmt::layer().json())
        .init();
}

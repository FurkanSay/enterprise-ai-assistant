//! Health endpoints — liveness + readiness.

use axum::{routing::get, Json, Router};
use serde_json::{json, Value};

pub fn router() -> Router {
    Router::new()
        .route("/health/live", get(live))
        .route("/health/ready", get(ready))
}

async fn live() -> Json<Value> {
    Json(json!({ "status": "ok", "service": "processing" }))
}

async fn ready() -> Json<Value> {
    // TODO: probe Redis connection + tantivy directory
    Json(json!({ "status": "ok", "service": "processing", "checks": { "redis": "TODO", "index": "TODO" } }))
}

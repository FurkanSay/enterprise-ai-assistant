//! gRPC ProcessingService implementation (BM25Search + Reindex).
//!
//! NOTE: Proto stub generation is wired through `libs/generated/rust/`
//! (see ADR-003). This file shows the integration shape; the actual
//! `processing::v1::*` types come from prost/tonic codegen.

use std::sync::Arc;

use anyhow::Result;
use bm25_index::Bm25Index;
use tonic::{Request, Response, Status};
use tracing::{info, instrument};

use crate::config::Config;

// Once code-gen is wired, replace the placeholder below with:
//   pub mod pb { tonic::include_proto!("processing.v1"); }
//   use pb::processing_service_server::{ProcessingService, ProcessingServiceServer};
//   ... etc.

pub struct ProcessingGrpcService {
    index: Arc<Bm25Index>,
}

impl ProcessingGrpcService {
    pub fn new(index: Arc<Bm25Index>) -> Self {
        Self { index }
    }

    /// Placeholder for the real gRPC handler. Once proto stubs exist:
    ///   #[tonic::async_trait]
    ///   impl pb::ProcessingService for ProcessingGrpcService { ... }
    #[instrument(skip(self))]
    pub fn bm25_search_handler(
        &self,
        tenant_id: &str,
        query: &str,
        top_k: usize,
    ) -> Result<Vec<bm25_index::BM25Hit>> {
        let hits = self.index.search(tenant_id, query, top_k)?;
        Ok(hits)
    }
}

pub async fn serve(cfg: Config, index: Arc<Bm25Index>) -> Result<()> {
    let _svc = ProcessingGrpcService::new(index);
    info!(addr = %cfg.bind_addr, "processing server listening (gRPC + HTTP)");

    // For now: only HTTP health endpoints are wired. gRPC server is plumbed in
    // when proto stubs land. This keeps the build green while infra is set up.
    let app = crate::health::router();
    let listener = tokio::net::TcpListener::bind(&cfg.bind_addr).await?;
    axum::serve(listener, app).await?;
    Ok(())
}

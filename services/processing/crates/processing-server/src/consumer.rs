//! Redis Streams consumer — picks up `doc.uploaded.v1` events and triggers
//! chunking + BM25 indexing.
//!
//! Idempotency: each event's `event_id` is the de-dup key. We use a simple
//! `SETNX` lock with a 24h TTL — first consumer wins.

use std::sync::Arc;

use anyhow::Result;
use bm25_index::Bm25Index;
use redis::AsyncCommands;
use tracing::{error, info, instrument, warn};

use crate::config::Config;

const STREAM_DOC_UPLOADED: &str = "stream:doc.uploaded";
const IDEM_TTL_SECONDS: u64 = 86_400;

pub async fn run_doc_uploaded_consumer(cfg: Config, index: Arc<Bm25Index>) -> Result<()> {
    let client = redis::Client::open(cfg.redis_url.as_str())?;
    let mut conn = client.get_multiplexed_async_connection().await?;

    // Idempotent group creation
    let _: Result<(), _> = redis::cmd("XGROUP")
        .arg("CREATE")
        .arg(STREAM_DOC_UPLOADED)
        .arg(&cfg.consumer_group)
        .arg("0")
        .arg("MKSTREAM")
        .query_async(&mut conn)
        .await;

    info!(group = %cfg.consumer_group, "consumer started");

    loop {
        // TODO: replace with XREADGROUP loop. For MVP iskelet we shape the
        // call site; the actual consume + process body lives here.
        tokio::time::sleep(std::time::Duration::from_secs(5)).await;
        process_pending(&mut conn, &cfg, &index).await.ok();
    }
}

#[instrument(skip_all)]
async fn process_pending(
    _conn: &mut redis::aio::MultiplexedConnection,
    _cfg: &Config,
    _index: &Bm25Index,
) -> Result<()> {
    // TODO:
    //   1. XREADGROUP doc.uploaded
    //   2. for each event:
    //      a. SETNX idempotency key — skip if already processed
    //      b. fetch document text from Documents service (gRPC GetDocumentText)
    //      c. chunker::chunk_text(...)
    //      d. index.index_document(tenant_id, doc_id, &chunks)
    //      e. XADD doc.chunked.v1 with chunk metadata
    //      f. XACK
    Ok(())
}

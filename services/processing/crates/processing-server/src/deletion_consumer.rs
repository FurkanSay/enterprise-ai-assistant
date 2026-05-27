//! Redis Streams consumer for `doc.deleted.v1`.
//!
//! When the Documents service hard-deletes a document, it publishes a
//! single XADD with `tenant_id` + `document_id`. This consumer purges
//! the matching points from Qdrant and the matching docs from tantivy
//! so search never surfaces orphan chunks.
//!
//! Separate stream + consumer-group from the upload pipeline because:
//!   - deletes must NOT block on a stuck upload (different failure
//!     modes — a bad PDF in `doc.uploaded.v1` shouldn't freeze cleanup)
//!   - the two flows scale independently
//!
//! Same XACK-on-success / leave-pending-on-failure semantics as the
//! upload consumer. Decode-failed entries are ACKed because they can
//! never decode — the payload is wrong, not transient.

use std::collections::HashMap;
use std::sync::Arc;

use anyhow::{anyhow, Context, Result};
use redis::aio::MultiplexedConnection;
use redis::streams::{StreamReadOptions, StreamReadReply};
use redis::AsyncCommands;
use tracing::{debug, error, info, warn};
use uuid::Uuid;

use crate::config::Config;
use crate::vector_store::VectorStore;
use bm25_index::Bm25Index;

const DELETED_STREAM_KEY: &str = "doc.deleted.v1";
const DELETED_CONSUMER_GROUP: &str = "processing-deletion";

pub struct DeletionConsumer {
    redis: MultiplexedConnection,
    consumer: String,
    qdrant: VectorStore,
    bm25: Arc<Bm25Index>,
}

#[derive(Debug)]
struct DocDeletedEvent {
    tenant_id: Uuid,
    document_id: Uuid,
}

impl DocDeletedEvent {
    fn from_fields(fields: &HashMap<String, String>) -> Result<Self> {
        let tenant_id = fields
            .get("tenant_id")
            .ok_or_else(|| anyhow!("missing tenant_id"))?;
        let document_id = fields
            .get("document_id")
            .ok_or_else(|| anyhow!("missing document_id"))?;
        Ok(Self {
            tenant_id: Uuid::parse_str(tenant_id)
                .map_err(|e| anyhow!("tenant_id not a UUID: {e}"))?,
            document_id: Uuid::parse_str(document_id)
                .map_err(|e| anyhow!("document_id not a UUID: {e}"))?,
        })
    }
}

impl DeletionConsumer {
    pub async fn connect(
        cfg: &Config,
        qdrant: VectorStore,
        bm25: Arc<Bm25Index>,
    ) -> Result<Self> {
        let client = redis::Client::open(cfg.redis_url.clone())
            .context("redis client open")?;
        let conn = client
            .get_multiplexed_async_connection()
            .await
            .context("redis async connect")?;
        let mut me = Self {
            redis: conn,
            consumer: cfg.consumer_name.clone(),
            qdrant,
            bm25,
        };
        me.ensure_group().await?;
        Ok(me)
    }

    async fn ensure_group(&mut self) -> Result<()> {
        let res: redis::RedisResult<()> = redis::cmd("XGROUP")
            .arg("CREATE")
            .arg(DELETED_STREAM_KEY)
            .arg(DELETED_CONSUMER_GROUP)
            .arg("0")
            .arg("MKSTREAM")
            .query_async(&mut self.redis)
            .await;
        match res {
            Ok(_) => {
                info!(
                    stream = DELETED_STREAM_KEY,
                    group = DELETED_CONSUMER_GROUP,
                    "deletion_consumer.group.created"
                );
                Ok(())
            }
            Err(e) if e.to_string().contains("BUSYGROUP") => {
                debug!(
                    stream = DELETED_STREAM_KEY,
                    group = DELETED_CONSUMER_GROUP,
                    "deletion_consumer.group.exists"
                );
                Ok(())
            }
            Err(e) => Err(anyhow!("XGROUP CREATE failed: {e}")),
        }
    }

    pub async fn run(mut self) -> Result<()> {
        info!(consumer = %self.consumer, "deletion_consumer.start");
        loop {
            if let Err(e) = self.poll_once().await {
                error!(error = %e, "deletion_consumer.poll.error");
                tokio::time::sleep(std::time::Duration::from_secs(2)).await;
            }
        }
    }

    async fn poll_once(&mut self) -> Result<()> {
        let opts = StreamReadOptions::default()
            .group(DELETED_CONSUMER_GROUP, &self.consumer)
            .block(5_000)
            .count(8);

        let reply: StreamReadReply = self
            .redis
            .xread_options(&[DELETED_STREAM_KEY], &[">"], &opts)
            .await
            .context("XREADGROUP")?;

        for stream in reply.keys {
            for entry in stream.ids {
                let entry_id = entry.id.clone();
                let fields = decode_fields(&entry.map);
                match DocDeletedEvent::from_fields(&fields) {
                    Ok(event) => match self.purge(&event).await {
                        Ok(()) => {
                            let _ack: i64 = self
                                .redis
                                .xack(DELETED_STREAM_KEY, DELETED_CONSUMER_GROUP, &[&entry_id])
                                .await
                                .unwrap_or(0);
                            info!(
                                tenant_id = %event.tenant_id,
                                document_id = %event.document_id,
                                "deletion_consumer.purged"
                            );
                        }
                        Err(e) => {
                            warn!(
                                document_id = %event.document_id,
                                error = format!("{e:#}"),
                                "deletion_consumer.purge.failed — leaving event pending"
                            );
                        }
                    },
                    Err(e) => {
                        warn!(entry_id = %entry_id, error = %e, "deletion_event.decode.failed");
                        let _ack: i64 = self
                            .redis
                            .xack(DELETED_STREAM_KEY, DELETED_CONSUMER_GROUP, &[&entry_id])
                            .await
                            .unwrap_or(0);
                    }
                }
            }
        }
        Ok(())
    }

    async fn purge(&self, event: &DocDeletedEvent) -> Result<()> {
        // Qdrant first — it has a delete-by-filter so the call is naturally
        // idempotent; the second store wipe stays safe if the first
        // succeeded and we crash before ACK.
        self.qdrant
            .delete_document(event.tenant_id, event.document_id)
            .await
            .context("qdrant delete_document")?;

        // tantivy writer is sync + mutex-locked; punt to a blocking pool so
        // we don't stall the runtime on a long commit.
        let bm25 = self.bm25.clone();
        let tenant_str = event.tenant_id.to_string();
        let doc_str = event.document_id.to_string();
        tokio::task::spawn_blocking(move || bm25.delete_document(&tenant_str, &doc_str))
            .await
            .context("bm25 delete spawn_blocking join")?
            .context("bm25 delete_document")?;
        Ok(())
    }
}

fn decode_fields(map: &HashMap<String, redis::Value>) -> HashMap<String, String> {
    map.iter()
        .filter_map(|(k, v)| match v {
            redis::Value::BulkString(bytes) => {
                String::from_utf8(bytes.clone()).ok().map(|s| (k.clone(), s))
            }
            redis::Value::SimpleString(s) => Some((k.clone(), s.clone())),
            _ => None,
        })
        .collect()
}

//! Redis Streams consumer with a consumer-group.
//!
//! Why a consumer-group instead of plain XREAD:
//!   - Multiple Processing replicas can join the same group and Redis hands
//!     each event to exactly one of them (work-stealing).
//!   - The "last id we acknowledged" is remembered server-side, so a restart
//!     resumes from the right place rather than the start of the stream.
//!
//! Failure model:
//!   - We XACK after the pipeline succeeds. If we crash mid-pipeline, Redis
//!     re-delivers on the next XREADGROUP because the entry stays in the
//!     pending-entries-list.
//!   - We do NOT XACK on pipeline error — the entry stays pending. That is
//!     a deliberate choice: a poison-pill event would block forever, which
//!     gets attention rather than silently dropping documents. A real
//!     production system would add a max-retries + dead-letter stream.

use std::collections::HashMap;

use anyhow::{anyhow, Context, Result};
use redis::aio::MultiplexedConnection;
use redis::streams::{StreamReadOptions, StreamReadReply};
use redis::AsyncCommands;
use tracing::{debug, error, info, warn};

use crate::config::Config;
use crate::event::DocUploadedEvent;
use crate::pipeline::Pipeline;

pub struct Consumer {
    redis: MultiplexedConnection,
    stream_key: String,
    group: String,
    consumer: String,
}

impl Consumer {
    pub async fn connect(cfg: &Config) -> Result<Self> {
        let client = redis::Client::open(cfg.redis_url.clone())
            .context("redis client open")?;
        let conn = client
            .get_multiplexed_async_connection()
            .await
            .context("redis async connect")?;
        let mut me = Self {
            redis: conn,
            stream_key: cfg.stream_key.clone(),
            group: cfg.consumer_group.clone(),
            consumer: cfg.consumer_name.clone(),
        };
        me.ensure_group().await?;
        Ok(me)
    }

    /// Create the consumer group lazily. `MKSTREAM` lets us create the group
    /// even if the stream does not exist yet — Documents may not have
    /// published the first event when we boot.
    async fn ensure_group(&mut self) -> Result<()> {
        let res: redis::RedisResult<()> = redis::cmd("XGROUP")
            .arg("CREATE")
            .arg(&self.stream_key)
            .arg(&self.group)
            .arg("0")
            .arg("MKSTREAM")
            .query_async(&mut self.redis)
            .await;
        match res {
            Ok(_) => {
                info!(stream = %self.stream_key, group = %self.group, "consumer.group.created");
                Ok(())
            }
            Err(e) if e.to_string().contains("BUSYGROUP") => {
                debug!(stream = %self.stream_key, group = %self.group, "consumer.group.exists");
                Ok(())
            }
            Err(e) => Err(anyhow!("XGROUP CREATE failed: {e}")),
        }
    }

    /// Run forever. Each loop iteration blocks for up to 5s waiting for the
    /// next batch of events.
    pub async fn run(mut self, pipeline: Pipeline) -> Result<()> {
        info!(consumer = %self.consumer, "consumer.start");
        loop {
            if let Err(e) = self.poll_once(&pipeline).await {
                error!(error = %e, "consumer.poll.error");
                tokio::time::sleep(std::time::Duration::from_secs(2)).await;
            }
        }
    }

    async fn poll_once(&mut self, pipeline: &Pipeline) -> Result<()> {
        let opts = StreamReadOptions::default()
            .group(&self.group, &self.consumer)
            .block(5_000)
            .count(8);

        let reply: StreamReadReply = self
            .redis
            .xread_options(&[&self.stream_key], &[">"], &opts)
            .await
            .context("XREADGROUP")?;

        for stream in reply.keys {
            for entry in stream.ids {
                let entry_id = entry.id.clone();
                let fields = decode_fields(&entry.map);
                match DocUploadedEvent::from_fields(&fields) {
                    Ok(event) => {
                        let started = std::time::Instant::now();
                        match pipeline.process(&event).await {
                            Ok(stats) => {
                                let _ack: i64 = self
                                    .redis
                                    .xack(&self.stream_key, &self.group, &[&entry_id])
                                    .await
                                    .unwrap_or(0);
                                info!(
                                    document_id = %event.document_id,
                                    chunks = stats.chunk_count,
                                    elapsed_ms = started.elapsed().as_millis() as u64,
                                    "pipeline.ok"
                                );
                            }
                            Err(e) => {
                                // `{:#}` walks the anyhow chain so the
                                // root cause is visible — plain `{}` only
                                // shows the outermost context layer.
                                warn!(
                                    document_id = %event.document_id,
                                    error = format!("{e:#}"),
                                    "pipeline.failed — leaving event pending"
                                );
                            }
                        }
                    }
                    Err(e) => {
                        warn!(entry_id = %entry_id, error = %e, "event.decode.failed");
                        // Decoding failed — the event will never decode; ACK so we
                        // don't loop on a malformed entry.
                        let _ack: i64 = self
                            .redis
                            .xack(&self.stream_key, &self.group, &[&entry_id])
                            .await
                            .unwrap_or(0);
                    }
                }
            }
        }
        Ok(())
    }
}

/// Turn the StreamId field map (redis::Value-typed) into a String map.
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

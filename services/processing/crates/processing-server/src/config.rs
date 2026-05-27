//! All env-driven configuration in one place. We deliberately do not pull
//! in a config crate — for a six-knob struct the value isn't there.

use anyhow::{Context, Result};

#[derive(Debug, Clone)]
pub struct Config {
    pub http_addr: String,

    pub redis_url: String,
    pub stream_key: String,
    pub consumer_group: String,
    pub consumer_name: String,

    pub minio_endpoint: String,
    pub minio_access_key: String,
    pub minio_secret_key: String,
    pub minio_bucket: String,

    pub qdrant_url: String,
    pub qdrant_collection: String,

    pub postgres_url: String,

    pub bm25_index_path: String,

    pub fastembed_cache_dir: String,
}

impl Config {
    pub fn from_env() -> Result<Self> {
        Ok(Self {
            http_addr: env_or("HTTP_ADDR", "0.0.0.0:8083"),
            redis_url: env_or("REDIS_URL", "redis://redis:6379"),
            stream_key: env_or("STREAM_KEY", "doc.uploaded.v1"),
            consumer_group: env_or("CONSUMER_GROUP", "processing"),
            consumer_name: env_or("CONSUMER_NAME", &default_consumer_name()),

            minio_endpoint: env_or("MINIO_ENDPOINT", "http://minio:9000"),
            minio_access_key: env_required("MINIO_ACCESS_KEY")?,
            minio_secret_key: env_required("MINIO_SECRET_KEY")?,
            minio_bucket: env_or("MINIO_BUCKET", "documents"),

            qdrant_url: env_or("QDRANT_URL", "http://qdrant:6334"),
            qdrant_collection: env_or("QDRANT_COLLECTION", "documents"),

            postgres_url: env_required("DATABASE_URL")?,

            bm25_index_path: env_or("BM25_INDEX_PATH", "/var/lib/processing/index"),

            fastembed_cache_dir: env_or("FASTEMBED_CACHE_DIR", "/var/lib/processing/models"),
        })
    }
}

fn env_or(key: &str, default: &str) -> String {
    std::env::var(key).unwrap_or_else(|_| default.to_string())
}

fn env_required(key: &str) -> Result<String> {
    std::env::var(key).with_context(|| format!("required env var missing: {key}"))
}

fn default_consumer_name() -> String {
    let host = std::env::var("HOSTNAME").unwrap_or_else(|_| "processing-1".to_string());
    format!("{host}-{}", std::process::id())
}

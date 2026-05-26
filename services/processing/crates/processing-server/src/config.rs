//! Runtime configuration — env-driven, validated at startup.

use anyhow::{Context, Result};
use std::path::PathBuf;

#[derive(Debug, Clone)]
pub struct Config {
    pub bind_addr: String,
    pub redis_url: String,
    pub otlp_endpoint: String,
    pub index_path: PathBuf,
    pub consumer_group: String,
    pub consumer_name: String,
}

impl Config {
    pub fn from_env() -> Result<Self> {
        Ok(Self {
            bind_addr: std::env::var("BIND_ADDR").unwrap_or_else(|_| "0.0.0.0:8083".into()),
            redis_url: std::env::var("REDIS_URL")
                .context("REDIS_URL is required")?,
            otlp_endpoint: std::env::var("OTEL_EXPORTER_OTLP_ENDPOINT")
                .unwrap_or_else(|_| "http://otel-collector:4317".into()),
            index_path: PathBuf::from(
                std::env::var("INDEX_PATH").unwrap_or_else(|_| "/var/lib/processing/index".into()),
            ),
            consumer_group: std::env::var("CONSUMER_GROUP")
                .unwrap_or_else(|_| "processing-svc".into()),
            consumer_name: std::env::var("CONSUMER_NAME")
                .unwrap_or_else(|_| "processing-1".into()),
        })
    }
}

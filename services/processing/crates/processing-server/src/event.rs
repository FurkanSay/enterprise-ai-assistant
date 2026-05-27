//! Wire shape of the doc.uploaded.v1 event. Mirrors the Java publisher
//! `com.aiasistan.documents.event.DocumentEventPublisher` — keep in sync.

use std::collections::HashMap;

use anyhow::{anyhow, Result};
use uuid::Uuid;

#[derive(Debug, Clone)]
pub struct DocUploadedEvent {
    pub document_id: Uuid,
    pub tenant_id: Uuid,
    pub uploader_user_id: Uuid,
    pub minio_object_key: String,
    pub text_object_key: String,
    pub mime_type: String,
    pub size_bytes: i64,
    pub sha256: String,
}

impl DocUploadedEvent {
    /// Redis Streams stores field/value pairs as bytes. The Java publisher
    /// sends Strings, so we expect a HashMap<String, String> after decoding.
    pub fn from_fields(fields: &HashMap<String, String>) -> Result<Self> {
        Ok(Self {
            document_id: parse_uuid(fields, "document_id")?,
            tenant_id: parse_uuid(fields, "tenant_id")?,
            uploader_user_id: parse_uuid(fields, "uploader_user_id")?,
            minio_object_key: pluck(fields, "minio_object_key")?.to_owned(),
            text_object_key: pluck(fields, "text_object_key")?.to_owned(),
            mime_type: pluck(fields, "mime_type")?.to_owned(),
            size_bytes: pluck(fields, "size_bytes")?.parse().map_err(|e| {
                anyhow!("size_bytes is not a number: {e}")
            })?,
            sha256: pluck(fields, "sha256")?.to_owned(),
        })
    }
}

fn pluck<'a>(map: &'a HashMap<String, String>, key: &str) -> Result<&'a str> {
    map.get(key)
        .map(|s| s.as_str())
        .ok_or_else(|| anyhow!("missing field in event: {key}"))
}

fn parse_uuid(map: &HashMap<String, String>, key: &str) -> Result<Uuid> {
    Uuid::parse_str(pluck(map, key)?)
        .map_err(|e| anyhow!("field {key} is not a UUID: {e}"))
}

//! MinIO downloader. MinIO speaks S3 — we use aws-sdk-s3 against the MinIO
//! endpoint, which keeps us free of MinIO-specific dependencies.

use anyhow::{Context, Result};
use aws_credential_types::Credentials;
use aws_sdk_s3::config::{BehaviorVersion, Region};
use aws_sdk_s3::Client;

use crate::config::Config;

pub struct ObjectStore {
    client: Client,
    bucket: String,
}

impl ObjectStore {
    pub async fn connect(cfg: &Config) -> Result<Self> {
        // MinIO is path-style and lives at a non-AWS endpoint, so we have to
        // override the SDK defaults. `Region::new("us-east-1")` is required
        // by the SigV4 signer even though MinIO ignores it.
        let creds = Credentials::new(
            &cfg.minio_access_key,
            &cfg.minio_secret_key,
            None,
            None,
            "static",
        );
        let s3_config = aws_sdk_s3::Config::builder()
            .behavior_version(BehaviorVersion::latest())
            .region(Region::new("us-east-1"))
            .endpoint_url(&cfg.minio_endpoint)
            .credentials_provider(creds)
            .force_path_style(true)
            .build();

        Ok(Self {
            client: Client::from_conf(s3_config),
            bucket: cfg.minio_bucket.clone(),
        })
    }

    /// Returns the object body as a UTF-8 string. We use this for the
    /// `text.txt` sidecar, which Documents always writes in UTF-8.
    pub async fn get_text(&self, object_key: &str) -> Result<String> {
        let resp = self
            .client
            .get_object()
            .bucket(&self.bucket)
            .key(object_key)
            .send()
            .await
            .with_context(|| format!("s3 get_object key={object_key}"))?;
        let bytes = resp
            .body
            .collect()
            .await
            .with_context(|| format!("s3 read body key={object_key}"))?
            .into_bytes();
        String::from_utf8(bytes.to_vec())
            .with_context(|| format!("text not valid UTF-8 key={object_key}"))
    }
}

//! Local embedder powered by fastembed-rs.
//!
//! Model choice: `BGEBaseENV15` (768 dims, ~120MB) — a strong general
//! baseline. Multilingual upgrades (bge-m3, mE5) live behind a config knob
//! we have not exposed yet because Phase E's smoke uses English-heavy text.
//!
//! Cache: model files are downloaded once into `cfg.fastembed_cache_dir`
//! (mounted as a Docker volume) so container restarts don't re-download.

use std::sync::Arc;

use anyhow::{Context, Result};
use fastembed::{EmbeddingModel, InitOptions, TextEmbedding};
use tokio::task;
use tracing::info;

use crate::config::Config;

#[derive(Clone)]
pub struct Embedder {
    inner: Arc<TextEmbedding>,
    pub dimensions: u64,
}

impl Embedder {
    pub fn load(cfg: &Config) -> Result<Self> {
        std::fs::create_dir_all(&cfg.fastembed_cache_dir).ok();
        let init = InitOptions::new(EmbeddingModel::BGEBaseENV15)
            .with_cache_dir(cfg.fastembed_cache_dir.clone().into())
            .with_show_download_progress(true);

        info!(
            cache = %cfg.fastembed_cache_dir,
            model = "BGEBaseENV15",
            "embedder.loading"
        );
        let model = TextEmbedding::try_new(init).context("fastembed init")?;
        // BGE Base = 768. The model itself does not expose `.dim()` directly,
        // so we hard-code here and assert at first embed.
        Ok(Self {
            inner: Arc::new(model),
            dimensions: 768,
        })
    }

    /// Batch embed. The fastembed call is CPU-bound (ONNX Runtime), so we
    /// `spawn_blocking` to keep the async runtime healthy.
    pub async fn embed(&self, texts: Vec<String>) -> Result<Vec<Vec<f32>>> {
        if texts.is_empty() {
            return Ok(Vec::new());
        }
        let model = self.inner.clone();
        let vectors = task::spawn_blocking(move || {
            let refs: Vec<&str> = texts.iter().map(|s| s.as_str()).collect();
            model.embed(refs, None)
        })
        .await
        .context("embedder spawn_blocking join")?
        .context("fastembed embed")?;

        if let Some(first) = vectors.first() {
            // Sanity check — surfaces a model mismatch the first time we run.
            if first.len() as u64 != self.dimensions {
                anyhow::bail!(
                    "embedder dim mismatch: model returned {}, configured {}",
                    first.len(),
                    self.dimensions
                );
            }
        }
        Ok(vectors)
    }
}

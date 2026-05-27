//! Postgres writer. Two responsibilities:
//!   1. Persist chunks_meta rows (one per chunk) so the dashboard can show
//!      "this document is N chunks".
//!   2. Transition documents.status PARSING → CHUNKING → EMBEDDING → READY,
//!      and on failure → FAILED with a reason.
//!
//! Tenant scoping: we connect as documents_user, which has BYPASSRLS=off,
//! so every txn must SET LOCAL app.current_tenant_id before touching the
//! documents_schema tables.
//!
//! Why no `with_tenant_tx<F: FnOnce>` helper: a higher-rank closure that
//! returns `BoxFuture<'c, R>` cannot capture borrows from its caller's
//! scope (the captured lifetime is not `'c`). Inlining the txn boilerplate
//! in each method keeps Rust's borrow checker happy without going through
//! a `Box<dyn Future + Send + 'static>` and cloning everything.

use anyhow::{Context, Result};
use chrono::Utc;
use sqlx::{postgres::PgPoolOptions, PgPool};
use uuid::Uuid;

use crate::config::Config;

#[derive(Clone)]
pub struct Db {
    pool: PgPool,
}

impl Db {
    pub async fn connect(cfg: &Config) -> Result<Self> {
        let pool = PgPoolOptions::new()
            .max_connections(8)
            .connect(&cfg.postgres_url)
            .await
            .context("pg connect")?;
        Ok(Self { pool })
    }

    pub async fn mark_status(
        &self,
        tenant_id: Uuid,
        document_id: Uuid,
        status: &str,
    ) -> Result<()> {
        let mut tx = self.pool.begin().await.context("pg begin")?;
        set_tenant(&mut tx, tenant_id).await?;

        let now = Utc::now();
        let rows = sqlx::query(
            "UPDATE documents_schema.documents
               SET status = $1, updated_at = $2
             WHERE id = $3",
        )
        .bind(status)
        .bind(now)
        .bind(document_id)
        .execute(&mut *tx)
        .await
        .context("update document status")?;
        if rows.rows_affected() == 0 {
            anyhow::bail!("document {document_id} not visible under tenant {tenant_id}");
        }

        tx.commit().await.context("pg commit")?;
        Ok(())
    }

    pub async fn mark_failed(
        &self,
        tenant_id: Uuid,
        document_id: Uuid,
        reason: &str,
    ) -> Result<()> {
        let mut tx = self.pool.begin().await.context("pg begin")?;
        set_tenant(&mut tx, tenant_id).await?;

        let now = Utc::now();
        sqlx::query(
            "UPDATE documents_schema.documents
               SET status = 'FAILED', failure_reason = $1, updated_at = $2
             WHERE id = $3",
        )
        .bind(reason)
        .bind(now)
        .bind(document_id)
        .execute(&mut *tx)
        .await
        .context("mark failed")?;

        tx.commit().await.context("pg commit")?;
        Ok(())
    }

    /// Persist chunks_meta rows and update documents.status + chunk_count
    /// in the same txn so a partial write never leaves the dashboard lying.
    pub async fn finalize_ready(
        &self,
        tenant_id: Uuid,
        document_id: Uuid,
        chunks: &[chunker::Chunk],
    ) -> Result<()> {
        let chunk_count = chunks.len() as i32;
        let mut tx = self.pool.begin().await.context("pg begin")?;
        set_tenant(&mut tx, tenant_id).await?;

        for chunk in chunks {
            let chunk_uuid = Uuid::parse_str(&chunk.chunk_id)
                .context("chunk_id is not a UUID")?;
            sqlx::query(
                "INSERT INTO documents_schema.document_chunks_meta
                   (chunk_id, document_id, tenant_id, sequence,
                    token_count, source_location)
                 VALUES ($1, $2, $3, $4, $5, $6)
                 ON CONFLICT (chunk_id) DO NOTHING",
            )
            .bind(chunk_uuid)
            .bind(document_id)
            .bind(tenant_id)
            .bind(chunk.sequence as i32)
            .bind((chunk.text.len() / 4) as i32) // ~4 chars/token approximation
            .bind(&chunk.source_location)
            .execute(&mut *tx)
            .await
            .context("insert chunks_meta")?;
        }

        let now = Utc::now();
        let rows = sqlx::query(
            "UPDATE documents_schema.documents
               SET status = 'READY',
                   chunk_count = $1,
                   updated_at = $2
             WHERE id = $3",
        )
        .bind(chunk_count)
        .bind(now)
        .bind(document_id)
        .execute(&mut *tx)
        .await
        .context("update document ready")?;
        if rows.rows_affected() == 0 {
            anyhow::bail!(
                "document {document_id} not visible for tenant {tenant_id} (RLS or wrong id)"
            );
        }

        tx.commit().await.context("pg commit")?;
        Ok(())
    }
}

/// SET LOCAL on the open txn so the next SQL statements see RLS scoped.
/// Auto-cleared by Postgres at commit/rollback.
async fn set_tenant<'c>(
    tx: &mut sqlx::Transaction<'c, sqlx::Postgres>,
    tenant_id: Uuid,
) -> Result<()> {
    sqlx::query("SELECT set_config('app.current_tenant_id', $1, true)")
        .bind(tenant_id.to_string())
        .execute(&mut **tx)
        .await
        .context("set tenant guc")?;
    Ok(())
}

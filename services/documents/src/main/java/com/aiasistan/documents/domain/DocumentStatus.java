package com.aiasistan.documents.domain;

/**
 * Lifecycle state of a document.
 *
 * Persisted as TEXT (see infra/postgres/init/06-documents-tables.sql) so the
 * column is stable even when we add new states. Order roughly matches the
 * happy-path flow:
 *
 *   UPLOADED → PARSING → CHUNKING → EMBEDDING → READY
 *                                        \─→ FAILED (any prior step)
 *
 * Documents service owns UPLOADED → PARSING transitions. Processing owns
 * CHUNKING; AI Engine owns EMBEDDING + READY. Each writer flips the row
 * and emits its own `doc.<state>.v1` event so downstream consumers can
 * pick up the work.
 */
public enum DocumentStatus {
    UPLOADED,
    PARSING,
    CHUNKING,
    EMBEDDING,
    READY,
    FAILED
}

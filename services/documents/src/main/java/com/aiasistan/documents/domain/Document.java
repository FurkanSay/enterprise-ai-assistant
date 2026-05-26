package com.aiasistan.documents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backed by documents_schema.documents.
 *
 * The table is created by infra/postgres/init/06-documents-tables.sql with
 * a FORCE ROW LEVEL SECURITY policy on tenant_id. We never call SQL like
 * "SELECT ... WHERE tenant_id = ?" — Postgres applies the filter for us
 * once the request scope sets app.current_tenant_id (see TenantContextFilter).
 */
@Entity
@Table(name = "documents", schema = "documents_schema")
public class Document implements Persistable<UUID> {

    /**
     * Assigned by the factory (newlyUploaded). We need the id BEFORE the
     * INSERT so we can use it to build the MinIO object key. Using
     * @UuidGenerator would defer assignment until Hibernate's persister
     * runs, which is too late.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "uploader_user_id", nullable = false, updatable = false)
    private UUID uploaderUserId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DocumentStatus status;

    @Column(name = "minio_object_key", nullable = false)
    private String minioObjectKey;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Persistable.isNew() — we assign ids manually, so without this flag
     * Spring Data would call merge() (with a wasteful SELECT) instead of
     * persist(). Flipped to false after the first persist or load.
     */
    @Transient
    private boolean isNew = true;

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    protected Document() {
        // for Hibernate
    }

    /**
     * Factory for newly-uploaded documents. status starts at UPLOADED;
     * Processing flips it to CHUNKING when its consumer picks up the event.
     */
    public static Document newlyUploaded(
            UUID id,
            UUID tenantId,
            UUID uploaderUserId,
            String title,
            String originalFilename,
            String mimeType,
            long sizeBytes,
            String sha256,
            String minioObjectKey) {
        Document doc = new Document();
        doc.id = id;
        doc.tenantId = tenantId;
        doc.uploaderUserId = uploaderUserId;
        doc.title = title;
        doc.originalFilename = originalFilename;
        doc.mimeType = mimeType;
        doc.sizeBytes = sizeBytes;
        doc.sha256 = sha256;
        doc.minioObjectKey = minioObjectKey;
        doc.status = DocumentStatus.UPLOADED;
        doc.chunkCount = 0;
        doc.createdAt = Instant.now();
        doc.updatedAt = doc.createdAt;
        return doc;
    }

    public void markFailed(String reason) {
        this.status = DocumentStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    // ─── Accessors ──────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUploaderUserId() { return uploaderUserId; }
    public String getTitle() { return title; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public DocumentStatus getStatus() { return status; }
    public String getMinioObjectKey() { return minioObjectKey; }
    public int getChunkCount() { return chunkCount; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

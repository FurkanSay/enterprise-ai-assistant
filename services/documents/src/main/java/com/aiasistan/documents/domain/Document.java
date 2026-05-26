package com.aiasistan.documents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Document aggregate — single source of truth for an uploaded file's metadata.
 *
 * <p>Multi-tenant: every row carries tenant_id (denormalized). RLS policy
 * defined in Flyway migration V1__init.sql enforces isolation at the DB level.
 */
@Entity
@Table(
    name = "documents",
    schema = "documents_schema",
    indexes = {
        @Index(name = "idx_documents_tenant", columnList = "tenant_id"),
        @Index(name = "idx_documents_status", columnList = "status"),
    })
public class Document {

    @Id
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "minio_object_key", nullable = false)
    private String minioObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Document() { /* JPA */ }

    public static Document create(
            UUID tenantId,
            UUID uploadedBy,
            String title,
            String originalFilename,
            String mimeType,
            long sizeBytes,
            String sha256,
            String minioObjectKey) {

        Document doc = new Document();
        Instant now = Instant.now();
        doc.tenantId = tenantId;
        doc.uploadedBy = uploadedBy;
        doc.title = title;
        doc.originalFilename = originalFilename;
        doc.mimeType = mimeType;
        doc.sizeBytes = sizeBytes;
        doc.sha256 = sha256;
        doc.minioObjectKey = minioObjectKey;
        doc.status = DocumentStatus.UPLOADED;
        doc.chunkCount = 0;
        doc.createdAt = now;
        doc.updatedAt = now;
        return doc;
    }

    public void transitionTo(DocumentStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    // Getters only — entity is mutated via behavior methods
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUploadedBy() { return uploadedBy; }
    public String getTitle() { return title; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getMinioObjectKey() { return minioObjectKey; }
    public DocumentStatus getStatus() { return status; }
    public int getChunkCount() { return chunkCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

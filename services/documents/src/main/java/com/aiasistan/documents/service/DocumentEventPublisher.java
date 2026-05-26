package com.aiasistan.documents.service;

import com.aiasistan.documents.domain.Document;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes Redis Streams events. Event envelope follows ADR-004 schema.
 */
@Component
public class DocumentEventPublisher {

    private static final String STREAM_DOC_UPLOADED = "stream:doc.uploaded";

    private final StringRedisTemplate redis;

    public DocumentEventPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void publishUploaded(Document doc) {
        Map<String, String> payload = Map.of(
                "event_id", UUID.randomUUID().toString(),
                "event_type", "doc.uploaded.v1",
                "occurred_at", Instant.now().toString(),
                "tenant_id", doc.getTenantId().toString(),
                "actor_user_id", doc.getUploadedBy().toString(),
                "actor_service", "documents",
                "doc_id", doc.getId().toString(),
                "minio_object_key", doc.getMinioObjectKey(),
                "mime_type", doc.getMimeType(),
                "size_bytes", String.valueOf(doc.getSizeBytes()),
                "sha256", doc.getSha256());

        ObjectRecord<String, Map<String, String>> record = StreamRecords.newRecord()
                .ofObject(payload)
                .withStreamKey(STREAM_DOC_UPLOADED);

        redis.opsForStream().add(record);
    }
}

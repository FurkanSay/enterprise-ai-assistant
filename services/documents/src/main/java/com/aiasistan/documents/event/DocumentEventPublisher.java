package com.aiasistan.documents.event;

import com.aiasistan.documents.domain.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes "doc.uploaded.v1" events on a Redis Stream. Processing service
 * tails the same stream and reacts: download from MinIO → chunk → embed.
 *
 * Why Redis Streams and not a Kafka topic? — KISS for this stage of the
 * project. Redis is already in the stack (cache, session store), it has
 * consumer-group semantics that survive restarts, and the event payload
 * here is tiny.
 *
 * Stream name is tenant-agnostic on purpose: every consumer reads tenant_id
 * from the payload itself, so a single stream can fan out cross-tenant
 * traffic. RLS still protects the database write paths downstream.
 */
@Component
public class DocumentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DocumentEventPublisher.class);

    static final String STREAM_KEY = "doc.uploaded.v1";

    private final StringRedisTemplate redis;

    public DocumentEventPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void publishUploaded(Document doc) {
        Map<String, String> payload = new HashMap<>();
        payload.put("event", "doc.uploaded.v1");
        payload.put("document_id", doc.getId().toString());
        payload.put("tenant_id", doc.getTenantId().toString());
        payload.put("uploader_user_id", doc.getUploaderUserId().toString());
        payload.put("minio_object_key", doc.getMinioObjectKey());
        payload.put("mime_type", doc.getMimeType());
        payload.put("size_bytes", Long.toString(doc.getSizeBytes()));
        payload.put("sha256", doc.getSha256());
        payload.put("uploaded_at", Instant.now().toString());

        ObjectRecord<String, Map<String, String>> record =
                StreamRecords.newRecord()
                        .ofObject(payload)
                        .withStreamKey(STREAM_KEY);

        RecordId id = redis.opsForStream().add(record);
        log.info("event.published stream={} record_id={} document_id={}",
                STREAM_KEY, id, doc.getId());
    }
}

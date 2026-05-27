package com.aiasistan.documents.event;

import com.aiasistan.documents.domain.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes "doc.uploaded.v1" events on a Redis Stream. Processing service
 * tails the same stream and reacts: download from MinIO → chunk → embed.
 *
 * Why a Spring event + AFTER_COMMIT and not a direct XADD: the upload
 * runs inside @Transactional, so a direct XADD inside the tx body would
 * publish BEFORE Hibernate commits. Processing would then race to update
 * a row that does not exist yet and fail.
 *
 * Why Redis Streams and not Kafka: KISS for this stage. Redis is already
 * in the stack, consumer-group semantics survive restarts, the payload
 * here is tiny.
 */
@Component
public class DocumentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DocumentEventPublisher.class);

    static final String STREAM_KEY = "doc.uploaded.v1";
    static final String DELETED_STREAM_KEY = "doc.deleted.v1";

    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher springEvents;

    public DocumentEventPublisher(StringRedisTemplate redis,
                                  ApplicationEventPublisher springEvents) {
        this.redis = redis;
        this.springEvents = springEvents;
    }

    /** Called from the service inside the @Transactional method. */
    public void publishUploaded(Document doc, String textObjectKey) {
        springEvents.publishEvent(new DocumentUploadedEvent(doc, textObjectKey));
    }

    /** Best-effort fire-and-forget — Processing tails this stream to
     *  purge Qdrant + tantivy entries for a deleted document. We do
     *  NOT route through Spring's AFTER_COMMIT phase here because the
     *  delete itself already committed by the time the service returns
     *  the boolean to the controller; an extra Spring event would just
     *  add ceremony. */
    public void publishDeleted(java.util.UUID tenantId, java.util.UUID documentId) {
        java.util.Map<String, String> payload = new java.util.HashMap<>();
        payload.put("event", "doc.deleted.v1");
        payload.put("tenant_id", tenantId.toString());
        payload.put("document_id", documentId.toString());
        payload.put("deleted_at", java.time.Instant.now().toString());

        org.springframework.data.redis.connection.stream.ObjectRecord<String, java.util.Map<String, String>> record =
                org.springframework.data.redis.connection.stream.StreamRecords.newRecord()
                        .ofObject(payload)
                        .withStreamKey(DELETED_STREAM_KEY);
        try {
            redis.opsForStream().add(record);
            log.info("event.published stream={} document_id={}", DELETED_STREAM_KEY, documentId);
        } catch (RuntimeException e) {
            log.error("event.publish.failed stream={} document_id={} error={}",
                    DELETED_STREAM_KEY, documentId, e.getMessage(), e);
        }
    }

    /**
     * Runs only after the upload tx commits successfully. If the tx rolls
     * back, this never fires — exactly what we want.
     *
     * NOTE: if Redis is unreachable here, the event is lost. For real
     * production we'd add a transactional outbox; for the smoke test this
     * is fine.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommittedUpload(DocumentUploadedEvent event) {
        Document doc = event.document();
        Map<String, String> payload = new HashMap<>();
        payload.put("event", "doc.uploaded.v1");
        payload.put("document_id", doc.getId().toString());
        payload.put("tenant_id", doc.getTenantId().toString());
        payload.put("uploader_user_id", doc.getUploaderUserId().toString());
        payload.put("minio_object_key", doc.getMinioObjectKey());
        payload.put("text_object_key", event.textObjectKey());
        payload.put("mime_type", doc.getMimeType());
        payload.put("size_bytes", Long.toString(doc.getSizeBytes()));
        payload.put("sha256", doc.getSha256());
        payload.put("uploaded_at", Instant.now().toString());

        ObjectRecord<String, Map<String, String>> record = StreamRecords.newRecord()
                .ofObject(payload)
                .withStreamKey(STREAM_KEY);

        try {
            RecordId id = redis.opsForStream().add(record);
            log.info("event.published stream={} record_id={} document_id={}",
                    STREAM_KEY, id, doc.getId());
        } catch (RuntimeException e) {
            // Outside the tx now — we can't roll back the upload. Log loudly so
            // an operator can re-publish manually from the DB row.
            log.error("event.publish.failed stream={} document_id={} error={}",
                    STREAM_KEY, doc.getId(), e.getMessage(), e);
        }
    }
}

package com.aiasistan.documents.service;

import com.aiasistan.documents.domain.Document;
import com.aiasistan.documents.domain.DocumentRepository;
import com.aiasistan.documents.event.DocumentEventPublisher;
import com.aiasistan.documents.parse.DocumentParser;
import com.aiasistan.documents.parse.DocumentParser.ParsedDocument;
import com.aiasistan.documents.storage.MinioStorage;
import com.aiasistan.documents.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates a document upload from REST handler down to storage + event.
 *
 * Flow (one @Transactional unit, so RLS GUC is set once by the aspect):
 *   1. SHA-256 the bytes — used both for dedup and as part of the event.
 *   2. If a row with the same sha256 already exists for this tenant
 *      (RLS-scoped), short-circuit and return it. Saves us from re-uploading
 *      identical files to MinIO and re-publishing events.
 *   3. Tika parse for content-type sniffing + a short preview.
 *   4. Stream the bytes to MinIO under <tenant>/<doc>/<safe>.
 *   5. Persist the metadata row.
 *   6. Publish "doc.uploaded.v1" so Processing can pick it up.
 *
 * Why hash + parse on bytes we already have in memory? — Spring's
 * MultipartFile materialises the upload (configured to 100 MB max). We
 * read the bytes twice: once to compute sha256, once to stream into MinIO.
 * For larger uploads we would switch to a streaming approach with a
 * DigestInputStream tee, but at this size the simpler shape wins.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Duration DOWNLOAD_URL_TTL = Duration.ofHours(1);

    private final DocumentRepository repository;
    private final MinioStorage storage;
    private final DocumentParser parser;
    private final DocumentEventPublisher events;

    public DocumentService(DocumentRepository repository,
                           MinioStorage storage,
                           DocumentParser parser,
                           DocumentEventPublisher events) {
        this.repository = repository;
        this.storage = storage;
        this.parser = parser;
        this.events = events;
    }

    public record LiteratureSource(UUID sessionId, String paperDoi, String paperTitle) {
        public static LiteratureSource none() {
            return new LiteratureSource(null, null, null);
        }
    }

    @Transactional
    public Document upload(MultipartFile file, String title) {
        return upload(file, title, LiteratureSource.none());
    }

    @Transactional
    public Document upload(MultipartFile file, String title, LiteratureSource source) {
        TenantContext.Current ctx = TenantContext.require();
        UUID tenantId = ctx.tenantId();
        UUID userId = ctx.userId();

        byte[] bytes = readAllBytes(file);
        String sha256 = sha256Hex(bytes);

        Optional<Document> existing = repository.findBySha256(sha256);
        if (existing.isPresent()) {
            log.info("upload.dedup sha256={} document_id={}", sha256, existing.get().getId());
            return existing.get();
        }

        ParsedDocument parsed = parser.parse(
                new java.io.ByteArrayInputStream(bytes),
                file.getOriginalFilename());

        UUID documentId = UUID.randomUUID();
        String objectKey = storage.buildObjectKey(tenantId, documentId, defaultFilename(file));
        String textObjectKey = storage.buildTextObjectKey(tenantId, documentId);

        try {
            storage.upload(
                    objectKey,
                    new java.io.ByteArrayInputStream(bytes),
                    bytes.length,
                    parsed.contentType());
            // Companion plain-text object — Processing reads this instead of
            // re-parsing the binary. Tika runs exactly once per document.
            byte[] textBytes = parsed.fullText().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            storage.upload(
                    textObjectKey,
                    new java.io.ByteArrayInputStream(textBytes),
                    textBytes.length,
                    "text/plain; charset=utf-8");
        } catch (Exception e) {
            throw new IllegalStateException("MinIO upload failed: " + e.getMessage(), e);
        }

        Document doc = Document.newlyUploaded(
                documentId,
                tenantId,
                userId,
                title != null && !title.isBlank() ? title : defaultFilename(file),
                defaultFilename(file),
                parsed.contentType() != null ? parsed.contentType() : file.getContentType(),
                bytes.length,
                sha256,
                objectKey,
                source.sessionId(),
                source.paperDoi(),
                source.paperTitle());

        Document saved = repository.save(doc);
        events.publishUploaded(saved, textObjectKey);
        log.info("upload.ok document_id={} bytes={} mime={}",
                saved.getId(), saved.getSizeBytes(), saved.getMimeType());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Document> list(int page, int size) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Document> listForSession(UUID sessionId, int page, int size) {
        return repository.findAllBySourceSessionIdOrderByCreatedAtDesc(
                sessionId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Optional<Document> findById(UUID id) {
        return repository.findById(id);
    }

    /**
     * Delete a document and everything that lives downstream:
     *   - documents_schema.document_chunks_meta (ON DELETE CASCADE)
     *   - documents_schema.documents row
     *   - MinIO objects (raw + text sidecar)
     *   - publish doc.deleted.v1 so Processing can purge Qdrant + tantivy
     *
     * Returns false if no document with that id is visible under the
     * current tenant. RLS is the only gate — we never query by tenant_id
     * explicitly.
     */
    @Transactional
    public boolean delete(UUID id) {
        Optional<Document> maybe = repository.findById(id);
        if (maybe.isEmpty()) {
            return false;
        }
        Document doc = maybe.get();

        // Best-effort MinIO cleanup. If MinIO is down the DB row still
        // gets deleted — the orphan object can be reclaimed by a sweeper
        // job later, but leaving the row around blocks re-upload of the
        // same filename forever.
        try {
            storage.remove(doc.getMinioObjectKey());
        } catch (Exception e) {
            log.warn("delete.minio.raw.failed key={} error={}",
                    doc.getMinioObjectKey(), e.getMessage());
        }
        try {
            storage.remove(storage.buildTextObjectKey(doc.getTenantId(), doc.getId()));
        } catch (Exception e) {
            log.warn("delete.minio.text.failed document_id={} error={}",
                    doc.getId(), e.getMessage());
        }

        repository.delete(doc);
        events.publishDeleted(doc.getTenantId(), doc.getId());
        log.info("delete.ok document_id={} title={}", doc.getId(), doc.getTitle());
        return true;
    }

    @Transactional(readOnly = true)
    public String presignedDownloadUrl(UUID id) {
        Document doc = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("document not found: " + id));
        try {
            return storage.presignedDownloadUrl(doc.getMinioObjectKey(), DOWNLOAD_URL_TTL);
        } catch (Exception e) {
            throw new IllegalStateException("MinIO presign failed: " + e.getMessage(), e);
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static byte[] readAllBytes(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read upload: " + e.getMessage(), e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String defaultFilename(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name != null && !name.isBlank() ? name : "upload-" + UUID.randomUUID();
    }
}

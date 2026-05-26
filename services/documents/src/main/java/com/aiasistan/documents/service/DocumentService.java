package com.aiasistan.documents.service;

import com.aiasistan.documents.common.TenantContext;
import com.aiasistan.documents.domain.Document;
import com.aiasistan.documents.domain.DocumentRepository;
import io.minio.errors.MinioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Document use cases — upload, list, get.
 *
 * <p>Single responsibility: orchestrate domain + infrastructure. No HTTP, no
 * MinIO/Redis impl details — depends on the {@link MinioStorage} and
 * {@link DocumentEventPublisher} abstractions.
 */
@Service
public class DocumentService {

    private final DocumentRepository repository;
    private final MinioStorage storage;
    private final DocumentEventPublisher publisher;

    public DocumentService(DocumentRepository repository,
                           MinioStorage storage,
                           DocumentEventPublisher publisher) {
        this.repository = repository;
        this.storage = storage;
        this.publisher = publisher;
    }

    @Transactional
    public Document upload(MultipartFile file)
            throws IOException, MinioException, NoSuchAlgorithmException, InvalidKeyException {

        TenantContext ctx = TenantContext.current();
        UUID documentId = UUID.randomUUID();

        String sha256 = sha256Of(file);
        String objectKey = storage.upload(
                ctx.tenantId(),
                documentId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getInputStream());

        Document doc = Document.create(
                ctx.tenantId(),
                ctx.userId(),
                deriveTitle(file.getOriginalFilename()),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                sha256,
                objectKey);

        repository.save(doc);
        publisher.publishUploaded(doc);

        return doc;
    }

    @Transactional(readOnly = true)
    public Document getById(UUID id) {
        TenantContext ctx = TenantContext.current();
        return repository.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
    }

    private static String sha256Of(MultipartFile file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (var in = file.getInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private static String deriveTitle(String filename) {
        if (filename == null || filename.isBlank()) {
            return "Untitled";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}

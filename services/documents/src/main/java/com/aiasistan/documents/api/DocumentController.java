package com.aiasistan.documents.api;

import com.aiasistan.documents.domain.Document;
import com.aiasistan.documents.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public REST surface for document CRUD.
 *
 * Path prefix is /api/v1/documents to leave room for /v2 if the wire shape
 * ever changes. Gateway rewrites the path so the frontend hits the same
 * URL regardless of which service owns it.
 *
 * Auth: there is no auth check here on purpose. The TenantContextFilter
 * already 401s anything without X-Tenant-Id, and RLS does the rest.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("file part is empty");
        }
        Document saved = service.upload(file, title);
        log.info("api.upload.ok document_id={} bytes={}", saved.getId(), saved.getSizeBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentResponse.from(saved));
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<Document> p = service.list(page, Math.min(size, 100));
        List<DocumentResponse> items = p.getContent().stream()
                .map(DocumentResponse::from)
                .toList();
        return Map.of(
                "items", items,
                "page", p.getNumber(),
                "size", p.getSize(),
                "total", p.getTotalElements());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> get(@PathVariable("id") UUID id) {
        return service.findById(id)
                .map(d -> ResponseEntity.ok(DocumentResponse.from(d)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/download-url")
    public Map<String, String> downloadUrl(@PathVariable("id") UUID id) {
        return Map.of("url", service.presignedDownloadUrl(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        boolean removed = service.delete(id);
        return removed
                ? ResponseEntity.noContent().build()   // 204
                : ResponseEntity.notFound().build();    // 404
    }
}

package com.aiasistan.documents.api;

import com.aiasistan.documents.domain.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape returned to the frontend. We intentionally don't expose
 * tenant_id or uploader_user_id — the caller already knows their own
 * identity (it's in the JWT), and exposing those values widens the API
 * surface for no benefit.
 *
 * Phase L adds three optional lineage fields populated only for
 * documents ingested via Deep Search: sourceSessionId, sourcePaperDoi,
 * sourcePaperTitle. Null on documents uploaded the normal way.
 */
public record DocumentResponse(
        UUID id,
        String title,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        String sha256,
        String status,
        int chunkCount,
        Instant createdAt,
        Instant updatedAt,
        UUID sourceSessionId,
        String sourcePaperDoi,
        String sourcePaperTitle) {

    public static DocumentResponse from(Document doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getOriginalFilename(),
                doc.getMimeType(),
                doc.getSizeBytes(),
                doc.getSha256(),
                doc.getStatus().name(),
                doc.getChunkCount(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                doc.getSourceSessionId(),
                doc.getSourcePaperDoi(),
                doc.getSourcePaperTitle());
    }
}

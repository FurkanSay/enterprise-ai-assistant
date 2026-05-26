package com.aiasistan.documents.api;

import com.aiasistan.documents.domain.Document;
import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String title,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        String sha256,
        String status,
        int chunkCount,
        Instant createdAt) {

    public static DocumentResponse of(Document doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getOriginalFilename(),
                doc.getMimeType(),
                doc.getSizeBytes(),
                doc.getSha256(),
                doc.getStatus().name(),
                doc.getChunkCount(),
                doc.getCreatedAt());
    }
}

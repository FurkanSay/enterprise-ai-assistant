package com.aiasistan.documents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from application.yml `minio.*` and the corresponding env vars
 * MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY / MINIO_BUCKET_DOCUMENTS.
 *
 * @param endpoint           Where the MinIO API is reachable from inside the
 *                           docker network — `http://minio:9000` in dev.
 * @param accessKey          Service-account access key.
 * @param secretKey          Service-account secret. NEVER log this.
 * @param bucketDocuments    Bucket name for raw uploaded document bytes.
 */
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        Bucket bucket
) {
    public record Bucket(String documents) { }
}

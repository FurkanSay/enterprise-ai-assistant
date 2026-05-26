package com.aiasistan.documents.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.util.UUID;

/**
 * Object storage wrapper — tenant-scoped object keys.
 * Key pattern: {tenantId}/{documentId}/{filename}
 */
@Component
public class MinioStorage {

    private final MinioClient client;
    private final String bucket;

    public MinioStorage(MinioClient client,
                        @Value("${minio.bucket.documents}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    public String upload(UUID tenantId,
                         UUID documentId,
                         String filename,
                         String contentType,
                         long size,
                         InputStream stream) throws MinioException, IOException,
                                                    NoSuchAlgorithmException, InvalidKeyException {
        String objectKey = buildObjectKey(tenantId, documentId, filename);
        client.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(stream, size, -1)
                        .contentType(contentType)
                        .build());
        return objectKey;
    }

    private static String buildObjectKey(UUID tenantId, UUID docId, String filename) {
        return "%s/%s/%s".formatted(tenantId, docId, filename);
    }
}

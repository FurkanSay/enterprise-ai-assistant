package com.aiasistan.documents.storage;

import com.aiasistan.documents.config.MinioProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

/**
 * Thin wrapper around MinioClient that enforces our tenant-scoped path
 * convention. Every object key starts with the tenant_id, so a stolen
 * presigned URL still only exposes one tenant's files.
 *
 * Object key shape:  <tenant_id>/<doc_id>/<safe_filename>
 */
@Service
public class MinioStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioStorage.class);

    private final MinioClient client;
    private final String bucket;

    public MinioStorage(MinioClient client, MinioProperties props) {
        this.client = client;
        this.bucket = props.bucket().documents();
    }

    public String buildObjectKey(UUID tenantId, UUID documentId, String filename) {
        String safe = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return tenantId + "/" + documentId + "/" + safe;
    }

    /**
     * Stream upload — no in-memory buffer, suitable for 100 MB+ files.
     */
    public void upload(String objectKey, InputStream stream, long size, String contentType)
            throws Exception {
        client.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(stream, size, -1)
                        .contentType(contentType)
                        .build()
        );
        log.info("minio.upload bucket={} key={} bytes={}", bucket, objectKey, size);
    }

    /**
     * Time-limited download URL. Default 1 hour. Returned to the frontend
     * for browser-direct download — Documents service never proxies the
     * file bytes through itself.
     */
    public String presignedDownloadUrl(String objectKey, Duration ttl) throws Exception {
        return client.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucket)
                        .object(objectKey)
                        .expiry((int) ttl.getSeconds())
                        .build()
        );
    }
}

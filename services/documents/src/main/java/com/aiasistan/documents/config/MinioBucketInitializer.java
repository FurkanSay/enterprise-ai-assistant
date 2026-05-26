package com.aiasistan.documents.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Idempotent bucket bootstrap. Runs once after the ApplicationContext is
 * fully refreshed, so it does not interfere with bean wiring.
 *
 * A fresh `docker compose up -d -v` starts with no manual bucket setup —
 * the documents bucket is created here if absent.
 */
@Configuration
public class MinioBucketInitializer {

    private static final Logger log = LoggerFactory.getLogger(MinioBucketInitializer.class);

    @Bean
    ApplicationRunner ensureBuckets(MinioClient client, MinioProperties props) {
        return args -> {
            String bucket = props.bucket().documents();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("minio.bucket.created bucket={}", bucket);
            } else {
                log.debug("minio.bucket.exists bucket={}", bucket);
            }
        };
    }
}

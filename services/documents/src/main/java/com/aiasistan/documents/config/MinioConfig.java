package com.aiasistan.documents.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO client wiring. The idempotent bucket bootstrap lives in
 * {@link MinioBucketInitializer} as a separate bean — it cannot live here
 * because calling the @Bean factory method from a @PostConstruct on the
 * same @Configuration class is interpreted by Spring's CGLIB proxy as a
 * self-referencing dependency, and fails startup.
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioProperties props) {
        return MinioClient.builder()
                .endpoint(props.endpoint())
                .credentials(props.accessKey(), props.secretKey())
                .build();
    }
}

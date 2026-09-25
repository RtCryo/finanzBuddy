package com.finanzbuddy.api.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        prefix = "app.minio",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Bean
    public MinioClient minioClient(
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.access-key}") String accessKey,
            @Value("${app.minio.secret-key}") String secretKey) {
        log.info("Configuring MinIO client for endpoint: {}", URI.create(endpoint).getAuthority());
        return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.minio",
            name = "create-bucket",
            havingValue = "true",
            matchIfMissing = true)
    public String documentsBucket(
            MinioClient minioClient, @Value("${app.minio.bucket}") String bucket) {
        try {
            boolean exists =
                    minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.warn(
                    "Could not verify or create MinIO bucket {} at startup; MinioHealthIndicator"
                            + " will report availability at runtime",
                    bucket,
                    e);
        }
        return bucket;
    }
}

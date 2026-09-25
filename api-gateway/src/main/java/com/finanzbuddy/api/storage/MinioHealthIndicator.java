package com.finanzbuddy.api.storage;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class MinioHealthIndicator implements HealthIndicator {

    private static final long CACHE_TTL_MS = 30_000;

    private final MinioClient minioClient;
    private final String bucket;
    private volatile Health cachedHealth = Health.unknown().build();
    private volatile long cacheExpiresAt = 0;

    public MinioHealthIndicator(
            MinioClient minioClient, @Value("${app.minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public Health health() {
        long now = System.currentTimeMillis();
        if (now < cacheExpiresAt) {
            return cachedHealth;
        }
        Health health;
        try {
            minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            health = Health.up().withDetail("storage", "minio").withDetail("bucket", bucket).build();
        } catch (Exception e) {
            health =
                    Health.down()
                            .withDetail("storage", "minio")
                            .withDetail("bucket", bucket)
                            .withException(e)
                            .build();
        }
        cachedHealth = health;
        cacheExpiresAt = now + CACHE_TTL_MS;
        return health;
    }
}

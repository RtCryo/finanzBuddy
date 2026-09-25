package com.finanzbuddy.api.storage;

import io.minio.MinioClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class MinioHealthIndicator implements HealthIndicator {

    private static final long CACHE_TTL_MS = 30_000;

    private final MinioClient minioClient;
    private volatile Health cachedHealth = Health.unknown().build();
    private volatile long cacheExpiresAt = 0;

    public MinioHealthIndicator(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public Health health() {
        long now = System.currentTimeMillis();
        if (now < cacheExpiresAt) {
            return cachedHealth;
        }
        Health health;
        try {
            minioClient.listBuckets();
            health = Health.up().withDetail("storage", "minio").build();
        } catch (Exception e) {
            health = Health.down().withDetail("storage", "minio").withException(e).build();
        }
        cachedHealth = health;
        cacheExpiresAt = now + CACHE_TTL_MS;
        return health;
    }
}

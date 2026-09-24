package com.finanzbuddy.api.storage;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;

    @Override
    public Health health() {
        try {
            minioClient.listBuckets();
            return Health.up().withDetail("storage", "minio").build();
        } catch (Exception e) {
            return Health.down().withDetail("storage", "minio").withException(e).build();
        }
    }
}

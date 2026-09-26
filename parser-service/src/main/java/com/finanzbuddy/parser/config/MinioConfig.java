package com.finanzbuddy.parser.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    private final String endpoint;
    private final String accessKey;
    private final String secretKey;

    public MinioConfig(
            @Value("${parser.storage.minio.endpoint}") String endpoint,
            @Value("${parser.storage.minio.access-key}") String accessKey,
            @Value("${parser.storage.minio.secret-key}") String secretKey) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }
}

package com.finanzbuddy.api.config;

import static org.mockito.Mockito.mock;

import com.finanzbuddy.api.event.DocumentUploadedEvent;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@Profile("test")
public class TestMockConfig {

    @Bean
    public MinioClient minioClient() {
        return mock(MinioClient.class);
    }

    @Bean
    public KafkaTemplate<String, DocumentUploadedEvent> kafkaTemplate() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, DocumentUploadedEvent> template = mock(KafkaTemplate.class);
        return template;
    }
}

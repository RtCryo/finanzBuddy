package com.finanzbuddy.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a single shared Jackson 2 {@link ObjectMapper} for outbox payload (de)serialization.
 *
 * <p>Spring Boot auto-configures a Jackson 3 ({@code tools.jackson}) mapper for HTTP message
 * conversion; it does not expose a Jackson 2 {@code com.fasterxml} mapper as a bean, so the outbox
 * components (which serialize/deserialize with Jackson 2) rely on this one. It intentionally does
 * not participate in web (de)serialization.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}

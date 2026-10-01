package com.finanzbuddy.api.security.adapter.out.apikey;

import com.finanzbuddy.api.security.port.out.ApiKeyPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyMatcherAdapter implements ApiKeyPort {

    private final String apiKey;

    public ApiKeyMatcherAdapter(@Value("${app.internal.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public boolean matches(String provided) {
        if (apiKey == null || apiKey.isBlank() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                apiKey.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }
}

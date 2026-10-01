package com.finanzbuddy.api.security.adapter.out.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.finanzbuddy.api.security.domain.model.TokenValidationResult;
import org.junit.jupiter.api.Test;

class JwtAccessTokenAdapterTest {

    private static final String SECRET = "ZmluYW56YnVkZHktYXBpLWdhdGV3YXktand0LXNlY3JldC1rZXk=";

    private final JwtAccessTokenAdapter adapter = new JwtAccessTokenAdapter(SECRET, 86400000);

    @Test
    void issuesTokenCarryingTheSubject() {
        String token = adapter.issue("test@example.com");

        assertThat(token).isNotBlank();
        TokenValidationResult result = adapter.validate(token);
        assertThat(result.status()).isEqualTo(TokenValidationResult.Status.VALID);
        assertThat(result.subject()).isEqualTo("test@example.com");
    }

    @Test
    void reportsExpiredTokenWithSubject() {
        JwtAccessTokenAdapter shortLived = new JwtAccessTokenAdapter(SECRET, -1000);
        String token = shortLived.issue("test@example.com");

        TokenValidationResult result = adapter.validate(token);

        assertThat(result.status()).isEqualTo(TokenValidationResult.Status.EXPIRED);
        assertThat(result.subject()).isEqualTo("test@example.com");
    }

    @Test
    void reportsMalformedTokenAsInvalid() {
        TokenValidationResult result = adapter.validate("not-a-jwt");

        assertThat(result.status()).isEqualTo(TokenValidationResult.Status.INVALID);
        assertThat(result.subject()).isNull();
    }

    @Test
    void reportsWrongSignatureAsInvalid() {
        String otherSecret = "YW5vdGhlci1zZWNyZXQta2V5LWZvci1qd3QtdGVzdGluZy0xMjM0";
        String token = new JwtAccessTokenAdapter(otherSecret, 86400000).issue("test@example.com");

        TokenValidationResult result = adapter.validate(token);

        assertThat(result.status()).isEqualTo(TokenValidationResult.Status.INVALID);
    }
}

package com.finanzbuddy.parser.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finanzbuddy.parser.document.DocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

class GatewayStatusClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final GatewayStatusClient client =
            new GatewayStatusClient(restTemplate, "http://localhost:9999/", "test-key");

    @Test
    void reportsStatusSuccessfully() {
        when(restTemplate.exchange(
                        any(String.class),
                        eq(HttpMethod.PUT),
                        any(HttpEntity.class),
                        eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        client.reportStatus(1L, DocumentStatus.PROCESSED, null);

        verify(restTemplate)
                .exchange(
                        eq("http://localhost:9999/internal/documents/1/status"),
                        eq(HttpMethod.PUT),
                        any(HttpEntity.class),
                        eq(Void.class));
    }

    @Test
    void throwsGatewayStatusExceptionOnNon2xxResponse() {
        when(restTemplate.exchange(
                        any(String.class),
                        eq(HttpMethod.PUT),
                        any(HttpEntity.class),
                        eq(Void.class)))
                .thenReturn(ResponseEntity.badRequest().build());

        assertThatThrownBy(() -> client.reportStatus(1L, DocumentStatus.PROCESSED, null))
                .isInstanceOf(GatewayStatusException.class)
                .hasMessageContaining("Status update failed with HTTP");
    }

    @Test
    void throwsGatewayStatusExceptionOnRestFailure() {
        when(restTemplate.exchange(
                        any(String.class),
                        eq(HttpMethod.PUT),
                        any(HttpEntity.class),
                        eq(Void.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertThatThrownBy(() -> client.reportStatus(1L, DocumentStatus.PROCESSED, null))
                .isInstanceOf(GatewayStatusException.class)
                .hasMessageContaining("Failed to report status");
    }

    @Test
    void stripsTrailingSlashFromBaseUrl() {
        GatewayStatusClient trailingSlashClient =
                new GatewayStatusClient(restTemplate, "http://gateway/", "key");
        when(restTemplate.exchange(
                        eq("http://gateway/internal/documents/2/status"),
                        eq(HttpMethod.PUT),
                        any(HttpEntity.class),
                        eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        trailingSlashClient.reportStatus(2L, DocumentStatus.PROCESSING, null);

        verify(restTemplate)
                .exchange(
                        eq("http://gateway/internal/documents/2/status"),
                        eq(HttpMethod.PUT),
                        any(HttpEntity.class),
                        eq(Void.class));
    }
}

package com.finanzbuddy.parser.client;

import com.finanzbuddy.parser.document.DocumentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GatewayStatusClient {

    private static final Logger logger = LoggerFactory.getLogger(GatewayStatusClient.class);

    public static final String HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;
    private final String internalApiKey;

    public GatewayStatusClient(
            RestClient.Builder restClientBuilder,
            @Value("${parser.gateway.base-url}") String baseUrl,
            @Value("${parser.gateway.internal-api-key:}") String internalApiKey) {
        String normalizedBaseUrl =
                baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restClient = restClientBuilder.baseUrl(normalizedBaseUrl).build();
        this.internalApiKey = internalApiKey;
    }

    @Retryable(
            retryFor = GatewayStatusException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    public void reportStatus(Long documentId, DocumentStatus status, String message) {
        String path = "/internal/documents/{documentId}/status";
        logger.info("Reporting document status: documentId={}, status={}", documentId, status);
        try {
            restClient
                    .put()
                    .uri(path, documentId)
                    .headers(
                            headers -> {
                                headers.setContentType(MediaType.APPLICATION_JSON);
                                if (internalApiKey != null && !internalApiKey.isBlank()) {
                                    headers.set(HEADER, internalApiKey);
                                }
                            })
                    .body(new StatusUpdateRequest(status, message))
                    .retrieve()
                    .toBodilessEntity();
            logger.info("Updated document status: documentId={}, status={}", documentId, status);
        } catch (RestClientException e) {
            logger.error(
                    "Failed to report status: documentId={}, status={}", documentId, status, e);
            throw new GatewayStatusException("Failed to report status", e);
        }
    }

    public record StatusUpdateRequest(DocumentStatus status, String message) {}
}

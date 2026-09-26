package com.finanzbuddy.parser.client;

import com.finanzbuddy.parser.document.DocumentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class GatewayStatusClient {

    private static final Logger logger = LoggerFactory.getLogger(GatewayStatusClient.class);

    public static final String HEADER = "X-Internal-Api-Key";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalApiKey;

    public GatewayStatusClient(
            RestTemplate restTemplate,
            @Value("${parser.gateway.base-url}") String baseUrl,
            @Value("${parser.gateway.internal-api-key}") String internalApiKey) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.internalApiKey = internalApiKey;
    }

    public void reportStatus(Long documentId, DocumentStatus status, String message) {
        String url = baseUrl + "/internal/documents/" + documentId + "/status";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
            if (internalApiKey != null && !internalApiKey.isBlank()) {
                headers.set(HEADER, internalApiKey);
            }

            StatusUpdateRequest requestBody = new StatusUpdateRequest(status, message);
            HttpEntity<StatusUpdateRequest> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Void> response =
                    restTemplate.exchange(url, HttpMethod.PUT, request, Void.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info(
                        "Updated document status: documentId={}, status={}", documentId, status);
            } else {
                logger.error(
                        "Failed to update document status: documentId={}, status={}, httpStatus={}",
                        documentId,
                        status,
                        response.getStatusCode());
                throw new GatewayStatusException(
                        "Status update failed with HTTP " + response.getStatusCode());
            }
        } catch (GatewayStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error(
                    "Failed to report status: documentId={}, status={}", documentId, status, e);
            throw new GatewayStatusException("Failed to report status", e);
        }
    }

    public record StatusUpdateRequest(DocumentStatus status, String message) {}
}

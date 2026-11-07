package com.finanzbuddy.parser.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.finanzbuddy.parser.document.DocumentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GatewayStatusClientTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    @Test
    void reportsStatusSuccessfully() {
        GatewayStatusClient client =
                new GatewayStatusClient(restClientBuilder, "http://localhost:9999", "test-key");
        mockServer
                .expect(requestTo("http://localhost:9999/internal/documents/1/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Internal-Api-Key", "test-key"))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andRespond(withSuccess());

        client.reportStatus(1L, DocumentStatus.PROCESSED, null);

        mockServer.verify();
    }

    @Test
    void sendsCountsOnlyWithProcessed() {
        GatewayStatusClient client =
                new GatewayStatusClient(restClientBuilder, "http://localhost:9999", "test-key");
        mockServer
                .expect(requestTo("http://localhost:9999/internal/documents/1/status"))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.savedTransactions").value(28))
                .andExpect(jsonPath("$.skippedDuplicates").value(300))
                .andRespond(withSuccess());

        client.reportProcessed(1L, 28, 300);

        mockServer.verify();
    }

    @Test
    void omitsCountsWhenNotKnown() {
        GatewayStatusClient client =
                new GatewayStatusClient(restClientBuilder, "http://localhost:9999", "test-key");
        mockServer
                .expect(requestTo("http://localhost:9999/internal/documents/1/status"))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.savedTransactions").doesNotExist())
                .andExpect(jsonPath("$.skippedDuplicates").doesNotExist())
                .andRespond(withSuccess());

        client.reportProcessed(1L, null, null);

        mockServer.verify();
    }

    @Test
    void otherStatusesCarryNoCounts() {
        GatewayStatusClient client =
                new GatewayStatusClient(restClientBuilder, "http://localhost:9999", "test-key");
        mockServer
                .expect(requestTo("http://localhost:9999/internal/documents/1/status"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.savedTransactions").doesNotExist())
                .andExpect(jsonPath("$.skippedDuplicates").doesNotExist())
                .andRespond(withSuccess());

        client.reportStatus(1L, DocumentStatus.FAILED, "boom");

        mockServer.verify();
    }

    @Test
    void throwsGatewayStatusExceptionOnNon2xxResponse() {
        GatewayStatusClient client =
                new GatewayStatusClient(restClientBuilder, "http://localhost:9999", "test-key");
        mockServer
                .expect(
                        ExpectedCount.times(3),
                        requestTo("http://localhost:9999/internal/documents/1/status"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.reportStatus(1L, DocumentStatus.PROCESSED, null))
                .isInstanceOf(GatewayStatusException.class)
                .hasMessageContaining("Failed to report status");
    }

    @Test
    void throwsGatewayStatusExceptionOnServerError() {
        GatewayStatusClient client =
                new GatewayStatusClient(restClientBuilder, "http://localhost:9999", "test-key");
        mockServer
                .expect(
                        ExpectedCount.times(3),
                        requestTo("http://localhost:9999/internal/documents/1/status"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.reportStatus(1L, DocumentStatus.PROCESSED, null))
                .isInstanceOf(GatewayStatusException.class)
                .hasMessageContaining("Failed to report status");
    }

    @Test
    void stripsTrailingSlashFromBaseUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GatewayStatusClient client = new GatewayStatusClient(builder, "http://gateway/", "key");
        server.expect(requestTo("http://gateway/internal/documents/2/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Internal-Api-Key", "key"))
                .andRespond(withSuccess());

        client.reportStatus(2L, DocumentStatus.PROCESSING, null);

        server.verify();
    }
}

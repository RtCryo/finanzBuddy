package com.finanzbuddy.parser.processing.adapter.out.status;

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

class RestClientStatusReporterAdapterTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    @Test
    void reportsStatusSuccessfully() {
        RestClientStatusReporterAdapter adapter =
                new RestClientStatusReporterAdapter(
                        restClientBuilder, "http://localhost:9999", "test-key");
        mockServer
                .expect(requestTo("http://localhost:9999/internal/documents/1/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Internal-Api-Key", "test-key"))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andRespond(withSuccess());

        adapter.report(1L, DocumentStatus.PROCESSED, null);

        mockServer.verify();
    }

    @Test
    void throwsGatewayStatusExceptionOnClientError() {
        RestClientStatusReporterAdapter adapter =
                new RestClientStatusReporterAdapter(
                        restClientBuilder, "http://localhost:9999", "test-key");
        mockServer
                .expect(
                        ExpectedCount.once(),
                        requestTo("http://localhost:9999/internal/documents/1/status"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.report(1L, DocumentStatus.PROCESSED, null))
                .isInstanceOf(GatewayStatusException.class)
                .hasMessageContaining("Failed to report status");
    }

    @Test
    void throwsGatewayStatusExceptionOnServerError() {
        RestClientStatusReporterAdapter adapter =
                new RestClientStatusReporterAdapter(
                        restClientBuilder, "http://localhost:9999", "test-key");
        mockServer
                .expect(
                        ExpectedCount.once(),
                        requestTo("http://localhost:9999/internal/documents/1/status"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.report(1L, DocumentStatus.PROCESSED, null))
                .isInstanceOf(GatewayStatusException.class)
                .hasMessageContaining("Failed to report status");
    }

    @Test
    void stripsTrailingSlashFromBaseUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientStatusReporterAdapter adapter =
                new RestClientStatusReporterAdapter(builder, "http://gateway/", "key");
        server.expect(requestTo("http://gateway/internal/documents/2/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Internal-Api-Key", "key"))
                .andRespond(withSuccess());

        adapter.report(2L, DocumentStatus.PROCESSING, null);

        server.verify();
    }
}

package com.finanzbuddy.api.document.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finanzbuddy.api.auth.dto.LoginRequest;
import com.finanzbuddy.api.auth.dto.RegisterRequest;
import com.finanzbuddy.api.document.dto.DocumentStatusUpdateRequest;
import com.finanzbuddy.api.document.outbox.OutboxRelay;
import com.finanzbuddy.api.entity.DocumentStatus;
import com.finanzbuddy.api.entity.OutboxEvent;
import com.finanzbuddy.api.repository.OutboxEventRepository;
import com.finanzbuddy.api.security.apikey.InternalApiKeyAuthFilter;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.concurrent.CompletableFuture;
import okhttp3.Headers;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class DocumentControllerIntegrationTest {

    private static final String API_KEY = "test-internal-api-key";

    @Autowired private WebApplicationContext context;

    @Autowired private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired private OutboxEventRepository outboxEventRepository;

    @Autowired private OutboxRelay outboxRelay;

    @Autowired private MinioClient minioClient;

    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        reset(kafkaTemplate, minioClient);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void uploadDocumentReturns201AndEnqueuesOutboxEvent() throws Exception {
        String token = registerAndLogin("docuser@example.com", "password123");

        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "report.csv", "text/csv", "name,amount\nTest,100".getBytes());

        MvcResult result =
                mockMvc.perform(
                                multipart("/documents")
                                        .file(file)
                                        .param("type", "CSV")
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isCreated())
                        .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        long documentId = response.get("id").asLong();
        assertThat(documentId).isPositive();
        assertThat(response.get("originalFilename").asText()).isEqualTo("report.csv");
        assertThat(response.get("type").asText()).isEqualTo("CSV");
        assertThat(response.get("status").asText()).isEqualTo(DocumentStatus.PENDING.name());

        OutboxEvent enqueued = findOutboxEvent(documentId);
        assertThat(enqueued).isNotNull();
        assertThat(enqueued.getPublishedAt()).isNull();
        assertThat(enqueued.getTopic()).isEqualTo("documents.csv");

        // The relay, not the request thread, delivers to Kafka.
        doReturn(CompletableFuture.completedFuture(null))
                .when(kafkaTemplate)
                .send(any(), any(), any());
        outboxRelay.publishPending();

        verify(kafkaTemplate, atLeastOnce()).send(any(), any(), any());
        assertThat(findOutboxEvent(documentId).getPublishedAt()).isNotNull();
    }

    @Test
    void uploadDocumentWithUnsupportedTypeReturns400() throws Exception {
        String token = registerAndLogin("doctype@example.com", "password123");
        MockMultipartFile file =
                new MockMultipartFile("file", "report.txt", "text/plain", "content".getBytes());

        mockMvc.perform(
                        multipart("/documents")
                                .file(file)
                                .param("type", "TXT")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadRejectsTypeNotAcceptedInMvp() throws Exception {
        String token = registerAndLogin("mvptype@example.com", "password123");
        MockMultipartFile file =
                new MockMultipartFile("file", "data.pdf", "application/pdf", "%PDF-1.4".getBytes());

        // PDF is a recognised type but not accepted in the MVP (parser support deferred).
        mockMvc.perform(
                        multipart("/documents")
                                .file(file)
                                .param("type", "PDF")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listAndGetWorkForOwnDocuments() throws Exception {
        String token = registerAndLogin("doclist@example.com", "password123");

        MockMultipartFile file =
                new MockMultipartFile("file", "data.csv", "text/csv", "a,b\n1,2".getBytes());

        long documentId = uploadDocument(token, file, "CSV");

        mockMvc.perform(get("/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/documents/" + documentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void listSupportsPagingAndFilenameSearch() throws Exception {
        String token = registerAndLogin("paging@example.com", "password123");
        uploadDocument(
                token,
                new MockMultipartFile("file", "alpha.csv", "text/csv", "a,b\n1,2".getBytes()),
                "CSV");
        uploadDocument(
                token,
                new MockMultipartFile("file", "beta.csv", "text/csv", "a,b\n1,2".getBytes()),
                "CSV");

        MvcResult page =
                mockMvc.perform(
                                get("/documents")
                                        .param("page", "0")
                                        .param("size", "1")
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode body = objectMapper.readTree(page.getResponse().getContentAsString());
        assertThat(body.get("totalElements").asLong()).isEqualTo(2);
        assertThat(body.get("totalPages").asInt()).isEqualTo(2);
        assertThat(body.get("content").size()).isEqualTo(1);
        assertThat(body.get("totalDocuments").asLong()).isEqualTo(2);
        assertThat(body.get("inProgressCount").asLong()).isEqualTo(2);

        MvcResult search =
                mockMvc.perform(
                                get("/documents")
                                        .param("filename", "alpha")
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode filtered = objectMapper.readTree(search.getResponse().getContentAsString());
        assertThat(filtered.get("totalElements").asLong()).isEqualTo(1);
        assertThat(filtered.get("content").get(0).get("originalFilename").asText())
                .isEqualTo("alpha.csv");
        assertThat(filtered.get("totalDocuments").asLong()).isEqualTo(2);
    }

    @Test
    void transactionEndpointReturnsOwnersTransactionsAndChecksDocumentOwner() throws Exception {
        String ownerToken = registerAndLogin("transaction-owner@example.com", "password123");
        String otherToken = registerAndLogin("transaction-other@example.com", "password123");
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "transactions.csv", "text/csv", "a,b\n1,2".getBytes());
        long documentId = uploadDocument(ownerToken, file, "CSV");

        // The transactions table is owned by the parser-service; recreate its shape here since the
        // test profile runs neither that service's Liquibase nor a JPA entity for it.
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS transactions (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + " document_id BIGINT NOT NULL, user_id BIGINT, booking_date DATE,"
                        + " value_date DATE, amount DECIMAL(19,4), currency VARCHAR(3),"
                        + " counterparty VARCHAR(500), description CLOB, category VARCHAR(255),"
                        + " raw_data CLOB, created_at TIMESTAMP)");

        mockMvc.perform(get("/documents/" + documentId + "/transactions"))
                .andExpect(status().isUnauthorized());

        // No parsed transactions yet -> empty list.
        MvcResult empty =
                mockMvc.perform(
                                get("/documents/" + documentId + "/transactions")
                                        .header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(empty.getResponse().getContentAsString()).isEqualTo("[]");

        // The parser-service writes normalized rows into the shared transactions table; the owner
        // must then see them through the gateway read view.
        jdbcTemplate.update(
                "INSERT INTO transactions (document_id, booking_date, value_date, amount,"
                        + " currency, counterparty, description, category) VALUES (?, ?, ?, ?, ?, ?,"
                        + " ?, ?)",
                documentId,
                Date.valueOf("2024-01-15"),
                Date.valueOf("2024-01-16"),
                new BigDecimal("42.5000"),
                "EUR",
                "ACME GmbH",
                "Invoice 4711",
                "Income");

        MvcResult listed =
                mockMvc.perform(
                                get("/documents/" + documentId + "/transactions")
                                        .header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode rows = objectMapper.readTree(listed.getResponse().getContentAsString());
        assertThat(rows.isArray()).isTrue();
        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.get(0).get("amount").asDouble()).isEqualTo(42.5);
        assertThat(rows.get(0).get("currency").asText()).isEqualTo("EUR");
        assertThat(rows.get(0).get("counterparty").asText()).isEqualTo("ACME GmbH");
        assertThat(rows.get(0).get("bookingDate").asText()).isEqualTo("2024-01-15");

        // A different user cannot see the document's transactions.
        mockMvc.perform(
                        get("/documents/" + documentId + "/transactions")
                                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadReturnsOwnersFileAndIsOwnerScoped() throws Exception {
        String ownerToken = registerAndLogin("download-owner@example.com", "password123");
        String otherToken = registerAndLogin("download-other@example.com", "password123");
        byte[] storedBytes = "a,b\n1,2".getBytes();
        MockMultipartFile file =
                new MockMultipartFile("file", "original.csv", "text/csv", storedBytes);
        long documentId = uploadDocument(ownerToken, file, "CSV");

        mockMvc.perform(get("/documents/" + documentId + "/download"))
                .andExpect(status().isUnauthorized());

        doReturn(
                        new GetObjectResponse(
                                Headers.of(),
                                "finanzbuddy-documents",
                                null,
                                "obj",
                                new ByteArrayInputStream(storedBytes)))
                .when(minioClient)
                .getObject(any(GetObjectArgs.class));

        MvcResult result =
                mockMvc.perform(
                                get("/documents/" + documentId + "/download")
                                        .header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isOk())
                        .andExpect(
                                header().string(
                                                "Content-Disposition",
                                                Matchers.containsString("original.csv")))
                        .andReturn();
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(storedBytes);

        mockMvc.perform(
                        get("/documents/" + documentId + "/download")
                                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void failedDocumentResponseIncludesErrorMessage() throws Exception {
        String token = registerAndLogin("failed-detail@example.com", "password123");
        MockMultipartFile file =
                new MockMultipartFile("file", "failed.csv", "text/csv", "a,b\n1,2".getBytes());
        long documentId = uploadDocument(token, file, "CSV");
        transition(documentId, DocumentStatus.PROCESSING, null);
        transition(documentId, DocumentStatus.FAILED, "The statement could not be parsed");

        MvcResult result =
                mockMvc.perform(
                                get("/documents/" + documentId)
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("errorMessage").asText())
                .isEqualTo("The statement could not be parsed");
    }

    @Test
    void getForeignDocumentReturns404() throws Exception {
        String ownerToken = registerAndLogin("owner@example.com", "password123");
        String otherToken = registerAndLogin("other@example.com", "password123");

        MockMultipartFile file =
                new MockMultipartFile("file", "data.csv", "text/csv", "a,b\n1,2".getBytes());

        long documentId = uploadDocument(ownerToken, file, "CSV");

        mockMvc.perform(
                        get("/documents/" + documentId)
                                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalStatusUpdateWithApiKeySucceeds() throws Exception {
        String token = registerAndLogin("parsertarget@example.com", "password123");
        MockMultipartFile file =
                new MockMultipartFile("file", "bank.csv", "text/csv", "a,b\n1,2".getBytes());
        long documentId = uploadDocument(token, file, "CSV");

        DocumentStatusUpdateRequest update =
                new DocumentStatusUpdateRequest(DocumentStatus.PROCESSING, null);
        MvcResult result =
                mockMvc.perform(
                                put("/internal/documents/" + documentId + "/status")
                                        .header(InternalApiKeyAuthFilter.HEADER, API_KEY)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(update)))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("status").asText()).isEqualTo(DocumentStatus.PROCESSING.name());
    }

    @Test
    void internalStatusUpdateWithoutApiKeyReturnsUnauthorized() throws Exception {
        DocumentStatusUpdateRequest update =
                new DocumentStatusUpdateRequest(DocumentStatus.PROCESSING, null);
        mockMvc.perform(
                        put("/internal/documents/1/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalStatusUpdateRejectsInvalidTransition() throws Exception {
        String token = registerAndLogin("parserbad@example.com", "password123");
        MockMultipartFile file =
                new MockMultipartFile("file", "x.csv", "text/csv", "a\n1".getBytes());
        long documentId = uploadDocument(token, file, "CSV");

        // PENDING -> PROCESSED is not an allowed transition.
        DocumentStatusUpdateRequest update =
                new DocumentStatusUpdateRequest(DocumentStatus.PROCESSED, null);
        mockMvc.perform(
                        put("/internal/documents/" + documentId + "/status")
                                .header(InternalApiKeyAuthFilter.HEADER, API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reprocessTransitionEnqueuesNewUploadEvent() throws Exception {
        String token = registerAndLogin("reprocess@example.com", "password123");
        MockMultipartFile file =
                new MockMultipartFile("file", "reprocess.csv", "text/csv", "a\n1".getBytes());
        long documentId = uploadDocument(token, file, "CSV");

        assertThat(countOutboxEvents(documentId)).isEqualTo(1);

        transition(documentId, DocumentStatus.PROCESSING, null);
        transition(documentId, DocumentStatus.FAILED, "boom");
        transition(documentId, DocumentStatus.PENDING, null);

        // The FAILED->PENDING reprocess must publish a second upload event so the document is
        // actually picked up again instead of sitting in PENDING forever.
        assertThat(countOutboxEvents(documentId)).isEqualTo(2);
    }

    @Test
    void uploadCleansUpWhenStorageFails() throws Exception {
        String token = registerAndLogin("storagefail@example.com", "password123");
        MockMultipartFile file =
                new MockMultipartFile("file", "fail.csv", "text/csv", "a\n1".getBytes());

        doThrow(new RuntimeException("minio down"))
                .when(minioClient)
                .putObject(any(PutObjectArgs.class));

        mockMvc.perform(
                        multipart("/documents")
                                .file(file)
                                .param("type", "CSV")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError());

        // The pending record must be rolled back and no upload event enqueued.
        MvcResult listResult =
                mockMvc.perform(get("/documents").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode page = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(page.get("totalElements").asInt()).isZero();
    }

    @Test
    void redeliveredProcessingStatusIsIdempotent() throws Exception {
        String token = registerAndLogin("reprocessing@example.com", "password123");
        MockMultipartFile file =
                new MockMultipartFile("file", "proc.csv", "text/csv", "a\n1".getBytes());
        long documentId = uploadDocument(token, file, "CSV");

        transition(documentId, DocumentStatus.PROCESSING, null);
        // Redelivery of the same status from an at-least-once parser must be a no-op 200, not a
        // 400 that would push the parser into a retry loop.
        transition(documentId, DocumentStatus.PROCESSING, null);
    }

    private void transition(long documentId, DocumentStatus status, String message)
            throws Exception {
        DocumentStatusUpdateRequest update = new DocumentStatusUpdateRequest(status, message);
        mockMvc.perform(
                        put("/internal/documents/" + documentId + "/status")
                                .header(InternalApiKeyAuthFilter.HEADER, API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
    }

    private long countOutboxEvents(long documentId) {
        return outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(documentId))
                .count();
    }

    private OutboxEvent findOutboxEvent(long documentId) {
        return outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(documentId))
                .findFirst()
                .orElse(null);
    }

    private long uploadDocument(String token, MockMultipartFile file, String type)
            throws Exception {
        MvcResult uploadResult =
                mockMvc.perform(
                                multipart("/documents")
                                        .file(file)
                                        .param("type", type)
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper
                .readTree(uploadResult.getResponse().getContentAsString())
                .get("id")
                .asLong();
    }

    private String registerAndLogin(String email, String password) throws Exception {
        RegisterRequest register = new RegisterRequest(email, password);
        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest(email, password);
        MvcResult result =
                mockMvc.perform(
                                post("/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(login)))
                        .andExpect(status().isOk())
                        .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("token")
                .asText();
    }
}

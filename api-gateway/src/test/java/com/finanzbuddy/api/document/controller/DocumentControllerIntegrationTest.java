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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
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

    @Autowired private MeterRegistry meterRegistry;

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
        assertThat(uploadCount("CSV", "success")).isGreaterThanOrEqualTo(1.0);
        assertThat(
                        meterRegistry
                                .get("api.documents.uploads")
                                .tag("type", "CSV")
                                .tag("outcome", "success")
                                .counter()
                                .getId()
                                .getTags())
                .containsExactlyInAnyOrder(Tag.of("type", "CSV"), Tag.of("outcome", "success"));
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
        ensureTransactionsTable();

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
    void aggregateTransactionEndpointRequiresAuthenticationAndScopesProcessedRowsToOwner()
            throws Exception {
        String ownerToken = registerAndLogin("history-owner@example.com", "password123");
        String otherToken = registerAndLogin("history-other@example.com", "password123");
        ensureTransactionsTable();
        long processedDocument = uploadDocument(ownerToken, csvFile("history.csv"), "CSV");
        long pendingDocument = uploadDocument(ownerToken, csvFile("pending-history.csv"), "CSV");
        long foreignDocument = uploadDocument(otherToken, csvFile("foreign-history.csv"), "CSV");
        markProcessed(processedDocument);
        markProcessed(foreignDocument);

        insertHistoryTransaction(
                processedDocument, "2024-03-12", "Owner shop", "Owner purchase", "Food");
        insertHistoryTransaction(
                pendingDocument, "2024-03-13", "Pending shop", "Pending row", "Food");
        insertHistoryTransaction(
                foreignDocument, "2024-03-14", "Foreign shop", "Foreign row", "Food");

        mockMvc.perform(get("/transactions")).andExpect(status().isUnauthorized());

        MvcResult ownerResult =
                mockMvc.perform(
                                get("/transactions")
                                        .header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode ownerBody = objectMapper.readTree(ownerResult.getResponse().getContentAsString());
        assertThat(ownerBody.get("totalElements").asLong()).isEqualTo(1);
        assertThat(ownerBody.get("content").get(0).get("description").asText())
                .isEqualTo("Owner purchase");
        assertThat(ownerBody.get("categories").get(0).asText()).isEqualTo("Food");

        MvcResult otherResult =
                mockMvc.perform(
                                get("/transactions")
                                        .header("Authorization", "Bearer " + otherToken))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode otherBody = objectMapper.readTree(otherResult.getResponse().getContentAsString());
        assertThat(otherBody.get("totalElements").asLong()).isEqualTo(1);
        assertThat(otherBody.get("content").get(0).get("description").asText())
                .isEqualTo("Foreign row");
    }

    @Test
    void categoryAndVersionEndpointsRequireAuthenticationAndEnforceTransactionOwnership()
            throws Exception {
        String ownerToken = registerAndLogin("category-owner@example.com", "password123");
        String otherToken = registerAndLogin("category-other@example.com", "password123");
        ensureTransactionsTable();
        long ownerDocument = uploadDocument(ownerToken, csvFile("category-owner.csv"), "CSV");
        long otherDocument = uploadDocument(otherToken, csvFile("category-other.csv"), "CSV");
        markProcessed(ownerDocument);
        markProcessed(otherDocument);
        insertHistoryTransaction(ownerDocument, "2024-04-01", "Owner shop", "Owner row", "Food");
        insertHistoryTransaction(otherDocument, "2024-04-02", "Other shop", "Other row", "Other");
        Long ownerTransaction =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM transactions WHERE document_id = ?",
                        Long.class,
                        ownerDocument);
        Long otherTransaction =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM transactions WHERE document_id = ?",
                        Long.class,
                        otherDocument);

        mockMvc.perform(get("/transactions/categories")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/transactions/" + ownerTransaction + "/versions"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        put("/transactions/" + ownerTransaction + "/category")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"category\":\"Housing\"}"))
                .andExpect(status().isUnauthorized());

        MvcResult initialVersions =
                mockMvc.perform(
                                get("/transactions/" + ownerTransaction + "/versions")
                                        .header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(
                        objectMapper
                                .readTree(initialVersions.getResponse().getContentAsString())
                                .get(0)
                                .get("transaction")
                                .get("category")
                                .asText())
                .isEqualTo("Food");

        MvcResult updated =
                mockMvc.perform(
                                put("/transactions/" + ownerTransaction + "/category")
                                        .header("Authorization", "Bearer " + ownerToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"category\":\"Housing\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(
                        objectMapper
                                .readTree(updated.getResponse().getContentAsString())
                                .get("category")
                                .asText())
                .isEqualTo("Housing");

        mockMvc.perform(
                        post("/transactions/categories")
                                .header("Authorization", "Bearer " + ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                java.util.Map.of(
                                                        "name",
                                                        "Travel",
                                                        "transactionId",
                                                        ownerTransaction))))
                .andExpect(status().isCreated());

        MvcResult versions =
                mockMvc.perform(
                                get("/transactions/" + ownerTransaction + "/versions")
                                        .header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode versionRows = objectMapper.readTree(versions.getResponse().getContentAsString());
        assertThat(versionRows.size()).isEqualTo(3);
        assertThat(versionRows.get(0).get("transaction").get("category").asText())
                .isEqualTo("Food");
        assertThat(versionRows.get(2).get("transaction").get("category").asText())
                .isEqualTo("Travel");

        MvcResult ownerCategories =
                mockMvc.perform(
                                get("/transactions/categories")
                                        .header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(
                        objectMapper
                                .readTree(ownerCategories.getResponse().getContentAsString())
                                .toString())
                .contains("Food", "Housing", "Travel");

        mockMvc.perform(
                        get("/transactions/" + ownerTransaction + "/versions")
                                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        put("/transactions/" + ownerTransaction + "/category")
                                .header("Authorization", "Bearer " + otherToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"category\":\"Stolen\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        post("/transactions/categories")
                                .header("Authorization", "Bearer " + otherToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                java.util.Map.of(
                                                        "name",
                                                        "Stolen",
                                                        "transactionId",
                                                        ownerTransaction))))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        get("/transactions/" + otherTransaction + "/versions")
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        MvcResult otherCategories =
                mockMvc.perform(
                                get("/transactions/categories")
                                        .header("Authorization", "Bearer " + otherToken))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(
                        objectMapper
                                .readTree(otherCategories.getResponse().getContentAsString())
                                .toString())
                .contains("Other")
                .doesNotContain("Travel", "Housing", "Food");
    }

    @Test
    void dashboardRequiresAuthenticationAndReturnsAllOwnerScopedMetrics() throws Exception {
        String ownerToken = registerAndLogin("dashboard-owner@example.com", "password123");
        String otherToken = registerAndLogin("dashboard-other@example.com", "password123");
        ensureTransactionsTable();

        long pendingDocument = uploadDocument(ownerToken, csvFile("dashboard-pending.csv"), "CSV");
        long processingDocument =
                uploadDocument(ownerToken, csvFile("dashboard-processing.csv"), "CSV");
        long processedDocument =
                uploadDocument(ownerToken, csvFile("dashboard-processed.csv"), "CSV");
        long failedDocument = uploadDocument(ownerToken, csvFile("dashboard-failed.csv"), "CSV");
        long foreignDocument = uploadDocument(otherToken, csvFile("dashboard-foreign.csv"), "CSV");
        transition(processingDocument, DocumentStatus.PROCESSING, null);
        markProcessed(processedDocument);
        transition(failedDocument, DocumentStatus.PROCESSING, null);
        transition(failedDocument, DocumentStatus.FAILED, "Invalid statement");
        markProcessed(foreignDocument);

        insertHistoryTransaction(processedDocument, "2024-03-10", "Shop A", "First", "Food");
        insertHistoryTransaction(processedDocument, "2024-03-11", "Shop A", "Repeated", "Food");
        insertHistoryTransaction(processedDocument, "2024-03-12", "  ", "Blank", "   ");
        insertHistoryTransaction(processedDocument, null, null, "Null values", null);
        insertHistoryTransaction(foreignDocument, "2024-03-13", "Other shop", "Other", "Other");

        mockMvc.perform(get("/dashboard")).andExpect(status().isUnauthorized());

        MvcResult ownerResult =
                mockMvc.perform(get("/dashboard").header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode ownerBody = objectMapper.readTree(ownerResult.getResponse().getContentAsString());
        assertThat(ownerBody.get("totalDocuments").asLong()).isEqualTo(4);
        assertThat(ownerBody.get("processedDocuments").asLong()).isEqualTo(1);
        assertThat(ownerBody.get("inProgressDocuments").asLong()).isEqualTo(2);
        assertThat(ownerBody.get("savedTransactions").asLong()).isEqualTo(4);
        assertThat(ownerBody.get("uniqueCategories").asLong()).isEqualTo(1);
        assertThat(ownerBody.get("uniqueCounterparties").asLong()).isEqualTo(1);

        MvcResult otherResult =
                mockMvc.perform(get("/dashboard").header("Authorization", "Bearer " + otherToken))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode otherBody = objectMapper.readTree(otherResult.getResponse().getContentAsString());
        assertThat(otherBody.get("totalDocuments").asLong()).isEqualTo(1);
        assertThat(otherBody.get("savedTransactions").asLong()).isEqualTo(1);
        assertThat(otherBody.get("uniqueCategories").asLong()).isEqualTo(1);
        assertThat(otherBody.get("uniqueCounterparties").asLong()).isEqualTo(1);
    }

    @Test
    void aggregateTransactionsSupportEmptyResultsInclusiveDatesTextAndCategoryFilters()
            throws Exception {
        String token = registerAndLogin("history-filters@example.com", "password123");
        String emptyToken = registerAndLogin("history-empty@example.com", "password123");
        ensureTransactionsTable();
        long documentId = uploadDocument(token, csvFile("filtered-history.csv"), "CSV");
        markProcessed(documentId);
        insertHistoryTransaction(
                documentId, "2024-01-01", "ACME GmbH", "Starting balance", "Income");
        insertHistoryTransaction(documentId, "2024-01-31", "ACME GmbH", "Monthly rent", "Income");
        insertHistoryTransaction(documentId, "2024-02-01", "Market", "Rent adjustment", "Expense");
        insertHistoryTransaction(documentId, null, "No date", "No booking date", null);

        MvcResult emptyResult =
                mockMvc.perform(
                                get("/transactions")
                                        .header("Authorization", "Bearer " + emptyToken))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode emptyBody = objectMapper.readTree(emptyResult.getResponse().getContentAsString());
        assertThat(emptyBody.get("content").size()).isZero();
        assertThat(emptyBody.get("totalElements").asLong()).isZero();

        mockMvc.perform(
                        get("/transactions")
                                .param("page", "-1")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        get("/transactions")
                                .param("size", "0")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        get("/transactions")
                                .param("from", "2024-02-01")
                                .param("to", "2024-01-01")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        MvcResult cappedPage =
                mockMvc.perform(
                                get("/transactions")
                                        .param("size", "101")
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(
                        objectMapper
                                .readTree(cappedPage.getResponse().getContentAsString())
                                .get("size")
                                .asInt())
                .isEqualTo(100);

        MvcResult combined =
                mockMvc.perform(
                                get("/transactions")
                                        .param("query", "aCmE")
                                        .param("from", "2024-01-31")
                                        .param("to", "2024-01-31")
                                        .param("category", "Income")
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode combinedBody = objectMapper.readTree(combined.getResponse().getContentAsString());
        assertThat(combinedBody.get("totalElements").asLong()).isEqualTo(1);
        assertThat(combinedBody.get("content").get(0).get("bookingDate").asText())
                .isEqualTo("2024-01-31");

        MvcResult descriptionSearch =
                mockMvc.perform(
                                get("/transactions")
                                        .param("query", "rEnT")
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode descriptionBody =
                objectMapper.readTree(descriptionSearch.getResponse().getContentAsString());
        assertThat(descriptionBody.get("totalElements").asLong()).isEqualTo(2);

        MvcResult categoryFilter =
                mockMvc.perform(
                                get("/transactions")
                                        .param("category", "Expense")
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode categoryBody =
                objectMapper.readTree(categoryFilter.getResponse().getContentAsString());
        assertThat(categoryBody.get("totalElements").asLong()).isEqualTo(1);
        assertThat(categoryBody.get("categories").toString()).contains("Expense", "Income");
        assertThat(categoryBody.get("hasUncategorized").asBoolean()).isTrue();
    }

    @Test
    void aggregateTransactionsSupportValidatedSorting() throws Exception {
        String token = registerAndLogin("history-sorting@example.com", "password123");
        ensureTransactionsTable();
        long documentId = uploadDocument(token, csvFile("sorted-history.csv"), "CSV");
        markProcessed(documentId);
        insertHistoryTransaction(documentId, "2024-03-10", "Bravo", "Third", "Food");
        insertHistoryTransaction(documentId, "2024-03-12", "Charlie", "Second", "Food");
        insertHistoryTransaction(documentId, "2024-03-11", "Alpha", "First", "Food");

        MvcResult defaultSort =
                mockMvc.perform(get("/transactions").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode defaultBody =
                objectMapper.readTree(defaultSort.getResponse().getContentAsString());
        assertThat(defaultBody.get("content").get(0).get("counterparty").asText())
                .isEqualTo("Charlie");

        MvcResult explicitSort =
                mockMvc.perform(
                                get("/transactions")
                                        .param("sortField", "counterparty")
                                        .param("sortDirection", "ASC")
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode explicitBody =
                objectMapper.readTree(explicitSort.getResponse().getContentAsString());
        assertThat(explicitBody.get("content").get(0).get("counterparty").asText())
                .isEqualTo("Alpha");

        mockMvc.perform(
                        get("/transactions")
                                .param("sortField", "amount; DROP TABLE transactions")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        get("/transactions")
                                .param("sortDirection", "sideways")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
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
        assertThat(uploadCount("CSV", "failure")).isGreaterThanOrEqualTo(1.0);
        assertThat(
                        meterRegistry
                                .get("api.documents.uploads")
                                .tag("type", "CSV")
                                .tag("outcome", "failure")
                                .counter()
                                .getId()
                                .getTags())
                .containsExactlyInAnyOrder(Tag.of("type", "CSV"), Tag.of("outcome", "failure"));
    }

    @Test
    void rejectedUnknownUploadTypeUsesBoundedOtherMetricLabel() throws Exception {
        String token = registerAndLogin("unknown-type@example.com", "password123");
        MockMultipartFile file =
                new MockMultipartFile("file", "report.txt", "text/plain", "content".getBytes());

        mockMvc.perform(
                        multipart("/documents")
                                .file(file)
                                .param("type", "TXT")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        assertThat(uploadCount("OTHER", "failure")).isGreaterThanOrEqualTo(1.0);
        assertThat(
                        meterRegistry
                                .get("api.documents.uploads")
                                .tag("type", "OTHER")
                                .tag("outcome", "failure")
                                .counter()
                                .getId()
                                .getTags())
                .containsExactlyInAnyOrder(Tag.of("type", "OTHER"), Tag.of("outcome", "failure"));
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

    private void ensureTransactionsTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS transactions (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                        + " document_id BIGINT NOT NULL, user_id BIGINT, booking_date DATE,"
                        + " value_date DATE, amount DECIMAL(19,4), currency VARCHAR(3),"
                        + " counterparty VARCHAR(500), description CLOB, category VARCHAR(255),"
                        + " raw_data CLOB, created_at TIMESTAMP)");
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS personal_transaction_categories ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,"
                        + "name VARCHAR(255) NOT NULL, normalized_name VARCHAR(255) NOT NULL,"
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,"
                        + "CONSTRAINT uk_test_personal_categories UNIQUE (user_id, normalized_name))");
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS transaction_versions ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY, transaction_id BIGINT NOT NULL,"
                        + "document_id BIGINT NOT NULL, user_id BIGINT NOT NULL, version_number INT NOT NULL,"
                        + "booking_date DATE, value_date DATE, amount DECIMAL(19,4), currency VARCHAR(3),"
                        + "counterparty VARCHAR(500), description CLOB, category VARCHAR(255),"
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,"
                        + "CONSTRAINT uk_test_transaction_versions UNIQUE (transaction_id, user_id, version_number))");
    }

    private MockMultipartFile csvFile(String name) {
        return new MockMultipartFile("file", name, "text/csv", "a,b\n1,2".getBytes());
    }

    private void markProcessed(long documentId) throws Exception {
        transition(documentId, DocumentStatus.PROCESSING, null);
        transition(documentId, DocumentStatus.PROCESSED, null);
    }

    private void insertHistoryTransaction(
            long documentId,
            String bookingDate,
            String counterparty,
            String description,
            String category) {
        Long userId =
                jdbcTemplate.queryForObject(
                        "SELECT user_id FROM documents WHERE id = ?", Long.class, documentId);
        jdbcTemplate.update(
                "INSERT INTO transactions (document_id, user_id, booking_date, value_date, amount,"
                        + " currency, counterparty, description, category) VALUES (?, ?, ?, ?,"
                        + " ?, 'EUR', ?, ?, ?)",
                documentId,
                userId,
                bookingDate == null ? null : Date.valueOf(bookingDate),
                bookingDate == null ? null : Date.valueOf(bookingDate),
                new BigDecimal("42.5000"),
                counterparty,
                description,
                category);
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

    private double uploadCount(String type, String outcome) {
        return meterRegistry
                .get("api.documents.uploads")
                .tag("type", type)
                .tag("outcome", outcome)
                .counter()
                .count();
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

package com.finanzbuddy.api.document.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finanzbuddy.api.auth.dto.LoginRequest;
import com.finanzbuddy.api.auth.dto.RegisterRequest;
import com.finanzbuddy.api.document.dto.DocumentStatusUpdateRequest;
import com.finanzbuddy.api.entity.DocumentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

    @Autowired private WebApplicationContext context;

    @Autowired private KafkaTemplate<Object, Object> kafkaTemplate;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        reset(kafkaTemplate);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void uploadDocumentReturns201AndStoresMetadata() throws Exception {
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
        assertThat(response.get("id").asLong()).isPositive();
        assertThat(response.get("originalFilename").asText()).isEqualTo("report.csv");
        assertThat(response.get("type").asText()).isEqualTo("CSV");
        assertThat(response.get("status").asText()).isEqualTo(DocumentStatus.PENDING.name());

        verify(kafkaTemplate).send(any(), any());
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
    void listAndGetAndUpdateStatusWorkForOwnDocuments() throws Exception {
        String token = registerAndLogin("doclist@example.com", "password123");

        MockMultipartFile file =
                new MockMultipartFile("file", "data.xml", "application/xml", "<root/>".getBytes());

        MvcResult uploadResult =
                mockMvc.perform(
                                multipart("/documents")
                                        .file(file)
                                        .param("type", "XML")
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isCreated())
                        .andReturn();
        long documentId =
                objectMapper
                        .readTree(uploadResult.getResponse().getContentAsString())
                        .get("id")
                        .asLong();

        mockMvc.perform(get("/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/documents/" + documentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        DocumentStatusUpdateRequest update =
                new DocumentStatusUpdateRequest(DocumentStatus.PROCESSING);
        mockMvc.perform(
                        put("/documents/" + documentId + "/status")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
    }

    @Test
    void getForeignDocumentReturns404() throws Exception {
        String ownerToken = registerAndLogin("owner@example.com", "password123");
        String otherToken = registerAndLogin("other@example.com", "password123");

        MockMultipartFile file =
                new MockMultipartFile("file", "data.pdf", "application/pdf", "%PDF".getBytes());

        MvcResult uploadResult =
                mockMvc.perform(
                                multipart("/documents")
                                        .file(file)
                                        .param("type", "PDF")
                                        .header("Authorization", "Bearer " + ownerToken))
                        .andExpect(status().isCreated())
                        .andReturn();
        long documentId =
                objectMapper
                        .readTree(uploadResult.getResponse().getContentAsString())
                        .get("id")
                        .asLong();

        mockMvc.perform(
                        get("/documents/" + documentId)
                                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
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

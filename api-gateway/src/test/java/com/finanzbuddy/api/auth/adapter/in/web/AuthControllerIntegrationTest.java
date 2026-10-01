package com.finanzbuddy.api.auth.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finanzbuddy.api.auth.adapter.in.web.dto.AuthResponse;
import com.finanzbuddy.api.auth.adapter.in.web.dto.LoginRequest;
import com.finanzbuddy.api.auth.adapter.in.web.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void registerReturns201AndToken() throws Exception {
        RegisterRequest request = new RegisterRequest("user1@example.com", "password123");

        MvcResult result =
                mockMvc.perform(
                                post("/auth/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andReturn();

        AuthResponse response =
                objectMapper.readValue(
                        result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(response.token()).isNotBlank();
    }

    @Test
    void registerDuplicateEmailReturns409() throws Exception {
        RegisterRequest request = new RegisterRequest("duplicate@example.com", "password123");
        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        MvcResult conflict =
                mockMvc.perform(
                                post("/auth/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isConflict())
                        .andReturn();
        JsonNode body = objectMapper.readTree(conflict.getResponse().getContentAsString());
        assertThat(body.get("detail").asText()).isEqualTo("Email already registered");
    }

    @Test
    void loginReturnsTokenForValidCredentials() throws Exception {
        RegisterRequest registerRequest =
                new RegisterRequest("loginuser@example.com", "password123");
        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest("loginuser@example.com", "password123");
        MvcResult result =
                mockMvc.perform(
                                post("/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(loginRequest)))
                        .andExpect(status().isOk())
                        .andReturn();

        AuthResponse response =
                objectMapper.readValue(
                        result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(response.token()).isNotBlank();
    }

    @Test
    void loginReturns401ForInvalidCredentials() throws Exception {
        LoginRequest loginRequest = new LoginRequest("unknown@example.com", "wrongpassword");

        MvcResult result =
                mockMvc.perform(
                                post("/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(loginRequest)))
                        .andExpect(status().isUnauthorized())
                        .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("detail").asText()).isEqualTo("Invalid credentials");
    }
}

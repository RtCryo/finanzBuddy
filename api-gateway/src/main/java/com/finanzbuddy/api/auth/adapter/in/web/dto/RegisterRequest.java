package com.finanzbuddy.api.auth.adapter.in.web.dto;

import com.finanzbuddy.api.auth.adapter.in.web.validation.ByteLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank @Email String email, @NotBlank @ByteLength(min = 6, max = 72) String password) {}

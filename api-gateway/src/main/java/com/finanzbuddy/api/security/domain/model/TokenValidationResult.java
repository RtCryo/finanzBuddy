package com.finanzbuddy.api.security.domain.model;

/** Outcome of parsing and validating an access token, free of any JWT-library type. */
public record TokenValidationResult(Status status, String subject) {

    public enum Status {
        VALID,
        EXPIRED,
        INVALID
    }

    public static TokenValidationResult valid(String subject) {
        return new TokenValidationResult(Status.VALID, subject);
    }

    public static TokenValidationResult expired(String subject) {
        return new TokenValidationResult(Status.EXPIRED, subject);
    }

    public static TokenValidationResult invalid() {
        return new TokenValidationResult(Status.INVALID, null);
    }
}

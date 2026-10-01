package com.finanzbuddy.api.auth.domain.model;

/** A user whose identity has been established; {@code email} is the token subject. */
public record AuthenticatedUser(String email) {}

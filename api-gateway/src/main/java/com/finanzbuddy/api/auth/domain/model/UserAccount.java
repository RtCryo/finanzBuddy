package com.finanzbuddy.api.auth.domain.model;

/** A persisted user with the stored password hash, used by the authentication adapters. */
public record UserAccount(String email, String passwordHash) {}

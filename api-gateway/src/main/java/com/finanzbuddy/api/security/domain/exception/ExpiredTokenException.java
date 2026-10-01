package com.finanzbuddy.api.security.domain.exception;

/** A bearer token parsed and verified but is past its expiry. Carries the subject for auditing. */
public class ExpiredTokenException extends RuntimeException {

    private final String subject;

    public ExpiredTokenException(String subject) {
        super("Expired access token");
        this.subject = subject;
    }

    public String subject() {
        return subject;
    }
}

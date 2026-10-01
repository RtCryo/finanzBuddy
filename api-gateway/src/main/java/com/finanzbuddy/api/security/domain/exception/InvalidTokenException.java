package com.finanzbuddy.api.security.domain.exception;

/** A bearer token could not be parsed or its signature did not verify. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
        super("Invalid access token");
    }
}

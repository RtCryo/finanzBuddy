package com.finanzbuddy.api.document.domain.exception;

public class UnauthorizedDocumentAccessException extends RuntimeException {

    public UnauthorizedDocumentAccessException(String message) {
        super(message);
    }
}

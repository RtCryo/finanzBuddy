package com.finanzbuddy.api.document.domain.exception;

public class InvalidDocumentRequestException extends RuntimeException {

    public InvalidDocumentRequestException(String message) {
        super(message);
    }
}

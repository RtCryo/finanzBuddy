package com.finanzbuddy.api.document.domain.exception;

public class ConcurrentDocumentModificationException extends RuntimeException {

    public ConcurrentDocumentModificationException(String message) {
        super(message);
    }
}

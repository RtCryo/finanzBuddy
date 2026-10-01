package com.finanzbuddy.api.document.domain.exception;

public class UploadFailedException extends RuntimeException {

    public UploadFailedException(String message) {
        super(message);
    }
}

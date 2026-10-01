package com.finanzbuddy.api.document.domain.exception;

/** Signals that an object-storage read/write failed; thrown by the file-storage adapter. */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

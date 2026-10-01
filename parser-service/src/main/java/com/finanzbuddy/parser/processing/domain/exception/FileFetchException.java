package com.finanzbuddy.parser.processing.domain.exception;

/** Domain failure raised when the stored document cannot be fetched. */
public class FileFetchException extends RuntimeException {

    public FileFetchException(String storagePath, Throwable cause) {
        super("Failed to fetch file: " + storagePath, cause);
    }
}

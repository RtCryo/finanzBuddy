package com.finanzbuddy.parser.processing.domain.exception;

/** Domain failure raised when transactions cannot be persisted. */
public class TransactionPersistenceException extends RuntimeException {

    public TransactionPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

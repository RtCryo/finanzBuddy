package com.finanzbuddy.parser.service;

public class DuplicateDocumentException extends RuntimeException {

    public DuplicateDocumentException(Long documentId) {
        super("Document was already processed: " + documentId);
    }
}

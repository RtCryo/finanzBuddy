package com.finanzbuddy.parser.processing.domain.model;

/**
 * Outcome of an idempotent transaction write: either the rows were written or the document was
 * already processed.
 */
public enum WriteResult {
    WRITTEN,
    DUPLICATE
}

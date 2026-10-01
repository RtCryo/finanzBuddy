package com.finanzbuddy.parser.processing.domain.model;

/** Terminal result of handling one document upload event. Failures surface as exceptions. */
public enum ProcessingOutcome {
    PROCESSED,
    DUPLICATE
}

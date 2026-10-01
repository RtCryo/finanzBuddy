package com.finanzbuddy.api.document.domain.model;

/** The bytes and original filename of a stored document, streamed back to its owner. */
public record DownloadedFile(byte[] content, String filename) {}

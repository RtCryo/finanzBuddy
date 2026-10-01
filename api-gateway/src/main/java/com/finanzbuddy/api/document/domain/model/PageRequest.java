package com.finanzbuddy.api.document.domain.model;

public record PageRequest(int page, int size, String filenameFilter) {}

package com.finanzbuddy.api.document.domain.model;

import java.util.List;

public record PageResult<T>(
        List<T> items, long totalElements, int totalPages, int page, int size) {}

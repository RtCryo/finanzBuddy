package com.finanzbuddy.api.document.dto;

import java.util.List;

public record TransactionTagRule(long id, long tagId, String mode, List<Condition> conditions) {
    public record Condition(String field, String operator, String value) {}
}

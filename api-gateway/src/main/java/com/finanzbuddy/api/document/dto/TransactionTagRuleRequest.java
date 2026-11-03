package com.finanzbuddy.api.document.dto;

import java.util.List;

public record TransactionTagRuleRequest(
        Long tagId,
        String tagName,
        String mode,
        List<TransactionTagRule.Condition> conditions,
        Boolean applyExisting,
        List<String> newCategoryNames) {
    public TransactionTagRuleRequest(
            Long tagId,
            String tagName,
            String mode,
            List<TransactionTagRule.Condition> conditions,
            Boolean applyExisting) {
        this(tagId, tagName, mode, conditions, applyExisting, List.of());
    }

    public TransactionTagRuleRequest(
            Long tagId,
            String mode,
            List<TransactionTagRule.Condition> conditions,
            Boolean applyExisting) {
        this(tagId, null, mode, conditions, applyExisting, List.of());
    }
}

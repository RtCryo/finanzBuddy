package com.finanzbuddy.api.document.service;

import com.finanzbuddy.api.document.dto.TransactionTagRule;
import com.finanzbuddy.api.document.dto.TransactionTagRule.Condition;
import com.finanzbuddy.api.document.dto.TransactionTagRuleRequest;
import com.finanzbuddy.api.repository.TransactionReadRepository.RuleCandidate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class TransactionTagRuleEvaluator {
    private TransactionTagRuleEvaluator() {}

    public static TransactionTagRule validated(long id, TransactionTagRuleRequest request) {
        if (request == null
                || (request.tagId() == null) == (request.tagName() == null)
                || (request.tagId() != null && request.tagId() <= 0)
                || (request.tagName() != null
                        && (request.tagName().trim().isEmpty()
                                || request.tagName().trim().length() > 255))
                || request.mode() == null
                || !Set.of("AND", "OR").contains(request.mode())
                || request.conditions() == null
                || request.conditions().isEmpty()) throw invalid();
        List<Condition> conditions =
                request.conditions().stream()
                        .map(
                                condition -> {
                                    if (condition == null
                                            || condition.field() == null
                                            || !Set.of("COUNTERPARTY", "DESCRIPTION", "CATEGORY")
                                                    .contains(condition.field())
                                            || condition.operator() == null
                                            || !Set.of("EXACT", "CONTAINS")
                                                    .contains(condition.operator())
                                            || condition.value() == null
                                            || condition.value().isBlank()
                                            || condition.value().trim().length() > 255)
                                        throw invalid();
                                    return new Condition(
                                            condition.field(),
                                            condition.operator(),
                                            condition.value().trim());
                                })
                        .toList();
        return new TransactionTagRule(
                id, request.tagId() == null ? 0 : request.tagId(), request.mode(), conditions);
    }

    public static boolean matches(TransactionTagRule rule, RuleCandidate candidate) {
        boolean and = rule.mode().equals("AND");
        for (Condition condition : rule.conditions()) {
            String field =
                    switch (condition.field()) {
                        case "COUNTERPARTY" -> candidate.counterparty();
                        case "DESCRIPTION" -> candidate.description();
                        case "CATEGORY" -> candidate.category();
                        default -> null;
                    };
            boolean match = false;
            if (field != null && !field.isBlank()) {
                String source = field.trim().toLowerCase(Locale.ROOT);
                String value = condition.value().trim().toLowerCase(Locale.ROOT);
                match =
                        condition.operator().equals("EXACT")
                                ? source.equals(value)
                                : source.contains(value);
            }
            if (and && !match) return false;
            if (!and && match) return true;
        }
        return and;
    }

    public static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rule");
    }
}

package com.finanzbuddy.api.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finanzbuddy.api.document.dto.TransactionTagRule;
import com.finanzbuddy.api.document.dto.TransactionTagRule.Condition;
import com.finanzbuddy.api.document.dto.TransactionTagRuleRequest;
import com.finanzbuddy.api.repository.TransactionReadRepository.RuleCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TransactionTagRuleEvaluatorTest {
    private static final RuleCandidate MATCH =
            new RuleCandidate(1, 2, " x ", " Payment yupi 42 ", " Travel ");

    @Test
    void evaluatesExactContainsAndFlatAndOr() {
        var exact = new Condition("COUNTERPARTY", "EXACT", " X ");
        var contains = new Condition("DESCRIPTION", "CONTAINS", " YUPI ");
        assertThat(TransactionTagRuleEvaluator.matches(rule("AND", exact, contains), MATCH))
                .isTrue();
        assertThat(
                        TransactionTagRuleEvaluator.matches(
                                rule("AND", exact, new Condition("DESCRIPTION", "EXACT", "No")),
                                MATCH))
                .isFalse();
        assertThat(
                        TransactionTagRuleEvaluator.matches(
                                rule("OR", exact, new Condition("DESCRIPTION", "EXACT", "No")),
                                MATCH))
                .isTrue();
        assertThat(
                        TransactionTagRuleEvaluator.matches(
                                rule(
                                        "OR",
                                        new Condition("COUNTERPARTY", "EXACT", "No"),
                                        new Condition("DESCRIPTION", "EXACT", "No")),
                                MATCH))
                .isFalse();
        assertThat(TransactionTagRuleEvaluator.matches(rule("AND", exact), MATCH)).isTrue();
    }

    @Test
    void blankAndMissingFieldsNeverMatch() {
        var condition = new Condition("DESCRIPTION", "CONTAINS", "x");
        assertThat(
                        TransactionTagRuleEvaluator.matches(
                                rule("OR", condition), new RuleCandidate(1, 2, "x", null, null)))
                .isFalse();
        assertThat(
                        TransactionTagRuleEvaluator.matches(
                                rule("AND", condition), new RuleCandidate(1, 2, "x", "  ", " ")))
                .isFalse();
    }

    @Test
    void categoryMatchesExactAndContainsButNeverBlankValues() {
        assertThat(
                        TransactionTagRuleEvaluator.matches(
                                rule("AND", new Condition("CATEGORY", "EXACT", " travel ")), MATCH))
                .isTrue();
        assertThat(
                        TransactionTagRuleEvaluator.matches(
                                rule("OR", new Condition("CATEGORY", "CONTAINS", "ave")), MATCH))
                .isTrue();
        for (String missing : new String[] {null, "", "  "}) {
            assertThat(
                            TransactionTagRuleEvaluator.matches(
                                    rule("OR", new Condition("CATEGORY", "CONTAINS", "a")),
                                    new RuleCandidate(1, 2, "x", "description", missing)))
                    .isFalse();
        }
    }

    @Test
    void rejectsMalformedInputAndTrimsValues() {
        assertThat(
                        TransactionTagRuleEvaluator.validated(
                                        0,
                                        new TransactionTagRuleRequest(
                                                1L,
                                                "AND",
                                                List.of(
                                                        new Condition(
                                                                "COUNTERPARTY", "EXACT", " X ")),
                                                false))
                                .conditions()
                                .getFirst()
                                .value())
                .isEqualTo("X");
        for (var invalid :
                List.of(
                        new TransactionTagRuleRequest(1L, "AND", List.of(), false),
                        new TransactionTagRuleRequest(
                                1L,
                                "AND",
                                List.of(new Condition("COUNTERPARTY", "EXACT", " ")),
                                false),
                        new TransactionTagRuleRequest(
                                1L, "OR", List.of(new Condition("GROUP", "EXACT", "X")), false),
                        new TransactionTagRuleRequest(
                                1L,
                                "XOR",
                                List.of(new Condition("DESCRIPTION", "CONTAINS", "X")),
                                false))) {
            assertThatThrownBy(() -> TransactionTagRuleEvaluator.validated(0, invalid))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    private static TransactionTagRule rule(String mode, Condition... conditions) {
        return new TransactionTagRule(1, 1, mode, List.of(conditions));
    }
}

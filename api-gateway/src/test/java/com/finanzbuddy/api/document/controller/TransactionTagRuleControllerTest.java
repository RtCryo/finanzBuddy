package com.finanzbuddy.api.document.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.finanzbuddy.api.document.service.TransactionTagRuleService;
import com.finanzbuddy.api.security.AccountPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class TransactionTagRuleControllerTest {
    private final TransactionTagRuleService service = mock(TransactionTagRuleService.class);
    private final TransactionTagRuleController controller =
            new TransactionTagRuleController(service);
    private final AccountPrincipal user =
            new AccountPrincipal(7L, UUID.randomUUID(), "owner@example.com");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void forwardsOneAtomicMutationWithHistoryChoice() {
        controller.create(
                user,
                mapper.readTree(
                        """
                {"tagId":4,"mode":"AND","conditions":[
                  {"field":"COUNTERPARTY","operator":"EXACT","value":"X"}],
                  "applyExisting":true}
                """));
        verify(service).create(eq(7L), any());
        controller.delete(user, 9, mapper.readTree("{" + "\"removeExisting\":false}"));
        verify(service).delete(7L, 9, false);
        controller.create(
                user,
                mapper.readTree(
                        """
                {"tagName":"New tag","mode":"AND","conditions":[
                  {"field":"CATEGORY","operator":"CONTAINS","value":"food"}]}
                """));
        verify(service)
                .create(
                        eq(7L),
                        eq(
                                new com.finanzbuddy.api.document.dto.TransactionTagRuleRequest(
                                        null,
                                        "New tag",
                                        "AND",
                                        java.util.List.of(
                                                new com.finanzbuddy.api.document.dto
                                                        .TransactionTagRule.Condition(
                                                        "CATEGORY", "CONTAINS", "food")),
                                        false)));
        controller.create(
                user,
                mapper.readTree(
                        """
                {"tagId":4,"mode":"AND","conditions":[
                  {"field":"CATEGORY","operator":"EXACT","value":"Utilities"}],
                  "newCategoryNames":["Utilities"],"applyExisting":false}
                """));
        verify(service)
                .create(
                        eq(7L),
                        eq(
                                new com.finanzbuddy.api.document.dto.TransactionTagRuleRequest(
                                        4L,
                                        null,
                                        "AND",
                                        java.util.List.of(
                                                new com.finanzbuddy.api.document.dto
                                                        .TransactionTagRule.Condition(
                                                        "CATEGORY", "EXACT", "Utilities")),
                                        false,
                                        java.util.List.of("Utilities"))));
    }

    @Test
    void rejectsNestedGroupsAndUnknownRequestFields() {
        assertThatThrownBy(
                        () ->
                                controller.create(
                                        user,
                                        mapper.readTree(
                                                """
                {"tagId":4,"mode":"AND","conditions":[
                  {"field":"COUNTERPARTY","operator":"EXACT","value":"X","group":[]}]}
                """)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(
                        () ->
                                controller.update(
                                        user,
                                        1,
                                        mapper.readTree(
                                                """
                {"tagId":4,"mode":"AND","conditions":[],"nested":{}}
                """)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.delete(user, 1, mapper.readTree("{}")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(
                        () ->
                                controller.create(
                                        user,
                                        mapper.readTree(
                                                """
                {"tagId":4,"tagName":"New","mode":"AND","conditions":[]}
                """)))
                .isInstanceOf(ResponseStatusException.class);
    }
}

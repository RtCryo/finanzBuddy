package com.finanzbuddy.api.document.controller;

import com.finanzbuddy.api.document.dto.TransactionTagRule;
import com.finanzbuddy.api.document.dto.TransactionTagRule.Condition;
import com.finanzbuddy.api.document.dto.TransactionTagRuleRequest;
import com.finanzbuddy.api.document.service.TransactionTagRuleService;
import com.finanzbuddy.api.security.AccountPrincipal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/transactions/rules")
public class TransactionTagRuleController {
    private final TransactionTagRuleService service;

    public TransactionTagRuleController(TransactionTagRuleService service) {
        this.service = service;
    }

    @GetMapping
    public List<TransactionTagRule> list(@AuthenticationPrincipal AccountPrincipal user) {
        return service.list(user.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionTagRule create(
            @AuthenticationPrincipal AccountPrincipal user, @RequestBody JsonNode request) {
        return service.create(user.userId(), parse(request));
    }

    @PutMapping("/{id}")
    public TransactionTagRule update(
            @AuthenticationPrincipal AccountPrincipal user,
            @PathVariable long id,
            @RequestBody JsonNode request) {
        return service.update(user.userId(), id, parse(request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AccountPrincipal user,
            @PathVariable long id,
            @RequestBody JsonNode request) {
        if (request == null
                || !request.isObject()
                || request.size() != 1
                || request.get("removeExisting") == null
                || !request.get("removeExisting").isBoolean())
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Existing transaction choice required");
        service.delete(user.userId(), id, request.get("removeExisting").booleanValue());
    }

    private static TransactionTagRuleRequest parse(JsonNode body) {
        if (body == null
                || !body.isObject()
                || body.get("mode") == null
                || !body.get("mode").isTextual()
                || body.get("conditions") == null
                || !body.get("conditions").isArray()) throw invalid();
        JsonNode tagId = body.get("tagId");
        JsonNode tagName = body.get("tagName");
        JsonNode choice = body.get("applyExisting");
        JsonNode newCategoryNames = body.get("newCategoryNames");
        if ((tagId == null) == (tagName == null)
                || (tagId != null && !tagId.isIntegralNumber())
                || (tagName != null && !tagName.isTextual())
                || body.size() != 3 + (choice == null ? 0 : 1) + (newCategoryNames == null ? 0 : 1)
                || (choice != null && !choice.isBoolean())
                || (newCategoryNames != null && !newCategoryNames.isArray())) throw invalid();
        List<Condition> conditions = new ArrayList<>();
        for (JsonNode item : body.get("conditions")) {
            if (!item.isObject()
                    || item.size() != 3
                    || item.get("field") == null
                    || !item.get("field").isTextual()
                    || item.get("operator") == null
                    || !item.get("operator").isTextual()
                    || item.get("value") == null
                    || !item.get("value").isTextual()) throw invalid();
            conditions.add(
                    new Condition(
                            item.get("field").textValue(),
                            item.get("operator").textValue(),
                            item.get("value").textValue()));
        }
        List<String> categoryNames = new ArrayList<>();
        if (newCategoryNames != null) {
            for (JsonNode name : newCategoryNames) {
                if (!name.isTextual()) throw invalid();
                categoryNames.add(name.textValue());
            }
        }
        return new TransactionTagRuleRequest(
                tagId == null ? null : tagId.longValue(),
                tagName == null ? null : tagName.textValue(),
                body.get("mode").textValue(),
                conditions,
                choice == null ? false : choice.booleanValue(),
                categoryNames);
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rule");
    }
}

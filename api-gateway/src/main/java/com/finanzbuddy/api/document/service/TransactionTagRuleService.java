package com.finanzbuddy.api.document.service;

import com.finanzbuddy.api.document.dto.TransactionTagRule;
import com.finanzbuddy.api.document.dto.TransactionTagRuleRequest;
import com.finanzbuddy.api.repository.TransactionCategoryRepository;
import com.finanzbuddy.api.repository.TransactionReadRepository;
import com.finanzbuddy.api.repository.TransactionTagRepository;
import com.finanzbuddy.api.repository.TransactionTagRuleRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransactionTagRuleService {
    private static final int BATCH_SIZE = 500;
    private final TransactionTagRepository tags;
    private final TransactionCategoryRepository categories;
    private final TransactionTagRuleRepository rules;
    private final TransactionReadRepository transactions;

    public TransactionTagRuleService(
            TransactionTagRepository tags,
            TransactionCategoryRepository categories,
            TransactionTagRuleRepository rules,
            TransactionReadRepository transactions) {
        this.tags = tags;
        this.categories = categories;
        this.rules = rules;
        this.transactions = transactions;
    }

    public List<TransactionTagRule> list(long userId) {
        return rules.findAll(userId);
    }

    @Transactional
    public TransactionTagRule create(long userId, TransactionTagRuleRequest request) {
        TransactionTagRule proposed = TransactionTagRuleEvaluator.validated(0, request);
        List<String> categoryNames = validatedCategoryNames(request, proposed);
        rules.lockOwner(userId);
        long tagId = resolveTag(userId, request, proposed.tagId());
        createCategories(userId, categoryNames);
        TransactionTagRule saved =
                rules.create(userId, tagId, proposed.mode(), proposed.conditions());
        if (Boolean.TRUE.equals(request.applyExisting())) apply(userId, List.of(saved), null);
        return saved;
    }

    @Transactional
    public TransactionTagRule update(long userId, long id, TransactionTagRuleRequest request) {
        TransactionTagRule proposed = TransactionTagRuleEvaluator.validated(id, request);
        List<String> categoryNames = validatedCategoryNames(request, proposed);
        rules.lockOwner(userId);
        if (!rules.exists(userId, id)) throw missing();
        long tagId = resolveTag(userId, request, proposed.tagId());
        createCategories(userId, categoryNames);
        TransactionTagRule saved =
                rules.update(userId, id, tagId, proposed.mode(), proposed.conditions());
        if (Boolean.TRUE.equals(request.applyExisting())) {
            rules.deleteContributions(userId, id);
            rules.pruneAssignments(userId);
            apply(userId, List.of(saved), null);
        }
        return saved;
    }

    @Transactional
    public void delete(long userId, long id, boolean removeExisting) {
        rules.lockOwner(userId);
        if (!rules.exists(userId, id)) throw missing();
        if (!removeExisting) rules.promoteContributions(userId, id);
        rules.delete(userId, id);
        if (removeExisting) rules.pruneAssignments(userId);
    }

    @Transactional
    public void applyImport(long userId, long documentId) {
        rules.lockOwner(userId);
        List<TransactionTagRule> all = rules.findAll(userId);
        if (!all.isEmpty()) apply(userId, all, documentId);
    }

    public void lockOwner(long userId) {
        rules.lockOwner(userId);
    }

    private void apply(long userId, List<TransactionTagRule> toApply, Long documentId) {
        long after = 0;
        while (true) {
            var page = transactions.findRuleCandidates(userId, documentId, after, BATCH_SIZE);
            if (page.isEmpty()) return;
            List<long[]> assignments = new ArrayList<>();
            List<long[]> contributions = new ArrayList<>();
            for (var candidate : page) {
                for (TransactionTagRule rule : toApply) {
                    if (!TransactionTagRuleEvaluator.matches(rule, candidate)) continue;
                    assignments.add(
                            new long[] {candidate.documentId(), candidate.id(), rule.tagId()});
                    contributions.add(
                            new long[] {
                                rule.id(), candidate.documentId(), candidate.id(), rule.tagId()
                            });
                }
            }
            if (!assignments.isEmpty()) {
                tags.ensureRuleAssignments(userId, assignments);
                rules.addContributions(userId, contributions);
            }
            after = page.getLast().id();
        }
    }

    private void requireOwnedTag(long userId, long tagId) {
        if (!tags.ownedTagExists(userId, tagId)) throw TransactionTagRuleEvaluator.invalid();
    }

    private long resolveTag(long userId, TransactionTagRuleRequest request, long tagId) {
        if (request.tagName() != null) {
            String name = request.tagName().trim();
            return tags.createOrFind(userId, name, name.toLowerCase(Locale.ROOT)).id();
        }
        requireOwnedTag(userId, tagId);
        return tagId;
    }

    private static List<String> validatedCategoryNames(
            TransactionTagRuleRequest request, TransactionTagRule proposed) {
        if (request.newCategoryNames() == null) throw TransactionTagRuleEvaluator.invalid();
        return request.newCategoryNames().stream()
                .map(TransactionCategoryService::normalizeCategory)
                .peek(
                        name -> {
                            if (proposed.conditions().stream()
                                    .noneMatch(
                                            condition ->
                                                    condition.field().equals("CATEGORY")
                                                            && condition
                                                                    .value()
                                                                    .equalsIgnoreCase(name)))
                                throw TransactionTagRuleEvaluator.invalid();
                        })
                .toList();
    }

    private void createCategories(long userId, List<String> names) {
        for (String name : names) {
            categories.createPersonalCategory(userId, name, name.toLowerCase(Locale.ROOT));
        }
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found");
    }
}

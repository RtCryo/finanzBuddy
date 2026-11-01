package com.finanzbuddy.api.document.service;

import com.finanzbuddy.api.document.dto.*;
import com.finanzbuddy.api.repository.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransactionTagService {
    private final UserRepository users;
    private final TransactionTagRepository tags;
    private final TransactionCategoryRepository categories;
    private final TransactionCategoryService categoryService;
    private final TransactionTemplate transaction;

    public TransactionTagService(UserRepository users, TransactionTagRepository tags,
            TransactionCategoryRepository categories, TransactionCategoryService categoryService,
            PlatformTransactionManager manager) {
        this.users = users; this.tags = tags; this.categories = categories; this.categoryService = categoryService;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private long owner(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();
    }

    public List<TransactionTag> list(String email) { return tags.findAll(owner(email)); }

    public List<TransactionTag> assigned(String email, long id) {
        long userId = owner(email);
        categoryService.requireOwnedTransaction(userId, id);
        return tags.findAssigned(userId, id);
    }

    public List<Long> validateSelection(String email, List<Long> ids, String mode) {
        if (!Set.of("AND", "OR").contains(mode)) throw invalidSelection();
        long userId = owner(email);
        if (ids == null || ids.isEmpty()) return List.of();
        Set<Long> available = new HashSet<>();
        tags.findAll(userId).forEach(tag -> available.add(tag.id()));
        if (ids.stream().anyMatch(id -> id == null || id <= 0 || !available.contains(id))) throw invalidSelection();
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    public TransactionEditResponse save(String email, long id, TransactionEditRequest request) {
        if (request == null || request.tags() == null
                || (request.category() != null && request.newCategoryName() != null)) throw invalidSelection();
        String category = request.category() == null ? null : TransactionCategoryService.normalizeCategory(request.category());
        String newCategory = request.newCategoryName() == null ? null : TransactionCategoryService.normalizeCategory(request.newCategoryName());
        for (var entry : request.tags()) {
            if (entry == null || (entry.id() == null) == (entry.name() == null)) throw invalidSelection();
            if (entry.name() != null) validName(entry.name());
        }
        long userId = owner(email);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return transaction.execute(status -> {
                    var source = categoryService.lockOwnedTransaction(userId, id);
                    Map<Long, TransactionTag> available = new HashMap<>();
                    tags.findAll(userId).forEach(tag -> available.put(tag.id(), tag));
                    Map<Long, TransactionTag> desired = new LinkedHashMap<>();
                    for (var entry : request.tags()) {
                        TransactionTag tag;
                        if (entry.id() != null) {
                            tag = available.get(entry.id());
                            if (tag == null) throw invalidSelection();
                        } else {
                            String name = validName(entry.name());
                            tag = tags.createOrFind(userId, name, name.toLowerCase(Locale.ROOT));
                        }
                        desired.put(tag.id(), tag);
                    }
                    String savedCategory = newCategory == null ? category : categories.createPersonalCategory(
                            userId, newCategory, newCategory.toLowerCase(Locale.ROOT));
                    TransactionResponse updated = savedCategory == null
                            ? categoryService.currentTransaction(userId, id)
                            : categoryService.changeCategory(userId, id, savedCategory);
                    tags.replace(userId, source.documentId(), id, new ArrayList<>(desired.values()));
                    return new TransactionEditResponse(updated, tags.findAssigned(userId, id));
                });
            } catch (DuplicateKeyException collision) {
                if (attempt == 2) throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction was modified concurrently", collision);
            }
        }
        throw new IllegalStateException("Retry exhausted");
    }

    private static String validName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 255) throw invalidSelection();
        return name.trim();
    }

    private static ResponseStatusException invalidSelection() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid tag selection");
    }
}

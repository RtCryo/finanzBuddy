package com.finanzbuddy.api.document.service;

import com.finanzbuddy.api.document.dto.PersonalCategoryResponse;
import com.finanzbuddy.api.document.dto.TransactionResponse;
import com.finanzbuddy.api.document.dto.TransactionVersionResponse;
import com.finanzbuddy.api.entity.User;
import com.finanzbuddy.api.repository.TransactionCategoryRepository;
import com.finanzbuddy.api.repository.TransactionCategoryRepository.TransactionSnapshot;
import com.finanzbuddy.api.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransactionCategoryService {

    private final UserRepository userRepository;
    private final TransactionCategoryRepository categoryRepository;
    private final TransactionTemplate transactionTemplate;

    public TransactionCategoryService(
            UserRepository userRepository,
            TransactionCategoryRepository categoryRepository,
            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<String> getAvailableCategories(String userEmail) {
        User user = getUser(userEmail);
        return categoryRepository.findAvailableCategories(user.getId());
    }

    public List<TransactionVersionResponse> getVersions(String userEmail, long transactionId) {
        User user = getUser(userEmail);
        TransactionSnapshot transaction = requireOwnedTransaction(user.getId(), transactionId);
        List<TransactionVersionResponse> versions =
                categoryRepository.findVersions(user.getId(), transactionId);
        if (!versions.isEmpty()) return versions;
        return List.of(new TransactionVersionResponse(1, transaction.transaction(), null));
    }

    public TransactionResponse updateCategory(
            String userEmail, long transactionId, String requestedCategory) {
        String category = normalizeCategory(requestedCategory);
        User user = getUser(userEmail);
        try {
            return transactionTemplate.execute(
                    status -> changeCategory(user.getId(), transactionId, category));
        } catch (DataIntegrityViolationException exception) {
            throw categoryConflict(exception);
        }
    }

    public PersonalCategoryResponse createCategoryAndAssign(
            String userEmail, long transactionId, String requestedName) {
        String name = normalizeCategory(requestedName);
        User user = getUser(userEmail);
        try {
            return transactionTemplate.execute(
                    status -> {
                        String savedName =
                                categoryRepository.createPersonalCategory(
                                        user.getId(), name, normalizeForComparison(name));
                        TransactionResponse transaction =
                                changeCategory(user.getId(), transactionId, savedName);
                        return new PersonalCategoryResponse(savedName, transaction);
                    });
        } catch (DataIntegrityViolationException exception) {
            throw categoryConflict(exception);
        }
    }

    private TransactionResponse changeCategory(long userId, long transactionId, String category) {
        TransactionSnapshot source = requireOwnedTransaction(userId, transactionId);
        List<TransactionVersionResponse> versions =
                categoryRepository.findVersions(userId, transactionId);
        TransactionResponse current =
                versions.isEmpty()
                        ? source.transaction()
                        : versions.getLast().transaction();
        if (category.equals(current.category())) return current;

        if (versions.isEmpty()) {
            categoryRepository.appendVersion(userId, source.documentId(), 1, source.transaction());
            categoryRepository.appendVersion(
                    userId,
                    source.documentId(),
                    2,
                    withCategory(source.transaction(), category));
        } else {
            categoryRepository.appendVersion(
                    userId,
                    source.documentId(),
                    versions.getLast().versionNumber() + 1,
                    withCategory(current, category));
        }
        return withCategory(current, category);
    }

    private TransactionSnapshot requireOwnedTransaction(long userId, long transactionId) {
        TransactionSnapshot transaction =
                categoryRepository.findOwnedTransaction(userId, transactionId);
        if (transaction == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found");
        }
        return transaction;
    }

    private User getUser(String userEmail) {
        return userRepository
                .findByEmail(userEmail)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category must not be blank");
        }
        String normalized = category.trim();
        if (normalized.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Category must be 255 characters or fewer");
        }
        return normalized;
    }

    private static String normalizeForComparison(String category) {
        return category.toLowerCase(Locale.ROOT);
    }

    private static TransactionResponse withCategory(
            TransactionResponse transaction, String category) {
        return new TransactionResponse(
                transaction.id(),
                transaction.bookingDate(),
                transaction.valueDate(),
                transaction.amount(),
                transaction.currency(),
                transaction.counterparty(),
                transaction.description(),
                category);
    }

    private static ResponseStatusException categoryConflict(Exception cause) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Transaction category was modified concurrently", cause);
    }
}

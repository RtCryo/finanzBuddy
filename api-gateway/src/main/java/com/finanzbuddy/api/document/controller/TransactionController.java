package com.finanzbuddy.api.document.controller;

import com.finanzbuddy.api.document.dto.TransactionHistoryResponse;
import com.finanzbuddy.api.document.dto.PersonalCategoryCreateRequest;
import com.finanzbuddy.api.document.dto.PersonalCategoryResponse;
import com.finanzbuddy.api.document.dto.TransactionCategoryUpdateRequest;
import com.finanzbuddy.api.document.dto.TransactionResponse;
import com.finanzbuddy.api.document.dto.TransactionVersionResponse;
import com.finanzbuddy.api.document.service.DocumentService;
import com.finanzbuddy.api.document.service.TransactionCategoryService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS =
            Set.of("valueDate", "amount", "currency", "counterparty", "description", "category");

    private final DocumentService documentService;
    private final TransactionCategoryService transactionCategoryService;

    public TransactionController(
            DocumentService documentService,
            TransactionCategoryService transactionCategoryService) {
        this.documentService = documentService;
        this.transactionCategoryService = transactionCategoryService;
    }

    @GetMapping
    public ResponseEntity<TransactionHistoryResponse> listTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "valueDate") String sortField,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be positive");
        }
        if (!SORT_FIELDS.contains(sortField)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field");
        }
        String normalizedDirection = sortDirection.toUpperCase(java.util.Locale.ROOT);
        if (!normalizedDirection.equals("ASC") && !normalizedDirection.equals("DESC")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort direction");
        }
        int boundedSize = Math.min(size, MAX_PAGE_SIZE);
        TransactionHistoryResponse response =
                documentService.getTransactionHistory(
                        userDetails.getUsername(),
                        PageRequest.of(page, boundedSize),
                        query,
                        from,
                        to,
                        category,
                        sortField,
                        normalizedDirection);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> listCategories(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                transactionCategoryService.getAvailableCategories(userDetails.getUsername()));
    }

    @PostMapping("/categories")
    public ResponseEntity<PersonalCategoryResponse> createCategory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody PersonalCategoryCreateRequest request) {
        if (request == null || request.transactionId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A transaction ID is required to assign a category");
        }
        PersonalCategoryResponse response =
                transactionCategoryService.createCategoryAndAssign(
                        userDetails.getUsername(), request.transactionId(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<TransactionVersionResponse>> listVersions(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(
                transactionCategoryService.getVersions(userDetails.getUsername(), id));
    }

    @PutMapping("/{id}/category")
    public ResponseEntity<TransactionResponse> updateCategory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody TransactionCategoryUpdateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category is required");
        }
        return ResponseEntity.ok(
                transactionCategoryService.updateCategory(
                        userDetails.getUsername(), id, request.category()));
    }
}

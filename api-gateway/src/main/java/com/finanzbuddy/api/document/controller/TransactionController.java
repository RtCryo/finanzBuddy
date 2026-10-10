package com.finanzbuddy.api.document.controller;

import com.finanzbuddy.api.document.dto.TransactionHistoryResponse;
import com.finanzbuddy.api.document.service.DocumentService;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentService documentService;

    public TransactionController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<TransactionHistoryResponse> listTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be positive");
        }
        int boundedSize = Math.min(size, MAX_PAGE_SIZE);
        TransactionHistoryResponse response =
                documentService.getTransactionHistory(
                        userDetails.getUsername(),
                        PageRequest.of(page, boundedSize),
                        query,
                        from,
                        to,
                        category);
        return ResponseEntity.ok(response);
    }
}

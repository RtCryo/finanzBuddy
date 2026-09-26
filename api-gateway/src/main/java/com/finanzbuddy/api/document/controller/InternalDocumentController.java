package com.finanzbuddy.api.document.controller;

import com.finanzbuddy.api.document.dto.DocumentResponse;
import com.finanzbuddy.api.document.dto.DocumentStatusUpdateRequest;
import com.finanzbuddy.api.document.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for trusted internal services (e.g. the parser service). Secured by a service API key
 * rather than a user JWT — see {@code SecurityConfig} and the {@code /internal/**} filter chain.
 */
@RestController
@RequestMapping("/internal/documents")
public class InternalDocumentController {

    private final DocumentService documentService;

    public InternalDocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<DocumentResponse> updateDocumentStatus(
            @PathVariable Long id, @Valid @RequestBody DocumentStatusUpdateRequest request) {
        return ResponseEntity.ok(documentService.updateDocumentStatus(id, request));
    }
}

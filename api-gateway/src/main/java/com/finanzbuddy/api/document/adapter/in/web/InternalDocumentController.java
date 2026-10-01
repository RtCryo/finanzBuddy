package com.finanzbuddy.api.document.adapter.in.web;

import com.finanzbuddy.api.document.adapter.in.web.dto.DocumentResponse;
import com.finanzbuddy.api.document.adapter.in.web.dto.DocumentStatusUpdateRequest;
import com.finanzbuddy.api.document.port.in.UpdateDocumentStatusUseCase;
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

    private final UpdateDocumentStatusUseCase updateDocumentStatusUseCase;

    public InternalDocumentController(UpdateDocumentStatusUseCase updateDocumentStatusUseCase) {
        this.updateDocumentStatusUseCase = updateDocumentStatusUseCase;
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<DocumentResponse> updateDocumentStatus(
            @PathVariable Long id, @Valid @RequestBody DocumentStatusUpdateRequest request) {
        return ResponseEntity.ok(
                DocumentWebMapper.toResponse(
                        updateDocumentStatusUseCase.updateStatus(
                                id, request.status(), request.message())));
    }
}

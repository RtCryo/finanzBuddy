package com.finanzbuddy.api.document.controller;

import com.finanzbuddy.api.document.dto.DocumentResponse;
import com.finanzbuddy.api.document.dto.TransactionResponse;
import com.finanzbuddy.api.document.service.DocumentService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> uploadDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) {
        DocumentResponse response =
                documentService.uploadDocument(userDetails.getUsername(), file, type);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> listDocuments(
            @AuthenticationPrincipal UserDetails userDetails, Pageable pageable) {
        return ResponseEntity.ok(
                documentService.listDocuments(userDetails.getUsername(), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocument(userDetails.getUsername(), id));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<TransactionResponse>> listTransactions(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        // Verify ownership before exposing the transaction endpoint for this document.
        documentService.getDocument(userDetails.getUsername(), id);

        // TODO: Load normalized transactions from the transaction read model once it is exposed to
        // the API Gateway. Keep TransactionResponse aligned with the parser's normalized fields.
        return ResponseEntity.ok(List.of());
    }

    @GetMapping(value = "/{id}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> downloadOriginalFile(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        documentService.getDocument(userDetails.getUsername(), id);

        // TODO: Stream the owner's original object from MinIO and set Content-Disposition with the
        // stored filename. This authenticated route establishes the contract; file streaming is
        // intentionally left for follow-up implementation.
        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED, "Original-file download is not implemented yet");
    }
}

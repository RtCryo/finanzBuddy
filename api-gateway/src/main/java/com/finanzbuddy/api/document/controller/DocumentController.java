package com.finanzbuddy.api.document.controller;

import com.finanzbuddy.api.document.dto.DocumentListResponse;
import com.finanzbuddy.api.document.dto.DocumentResponse;
import com.finanzbuddy.api.document.dto.DownloadedFile;
import com.finanzbuddy.api.document.dto.TransactionResponse;
import com.finanzbuddy.api.document.service.DocumentService;
import com.finanzbuddy.api.security.AccountPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> uploadDocument(
            @AuthenticationPrincipal AccountPrincipal userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) {
        DocumentResponse response =
                documentService.uploadDocument(userDetails.userId(), file, type);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<DocumentListResponse> listDocuments(
            @AuthenticationPrincipal AccountPrincipal userDetails,
            @RequestParam(required = false) String filename,
            Pageable pageable) {
        return ResponseEntity.ok(
                documentService.listDocuments(userDetails.userId(), pageable, filename));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(
            @AuthenticationPrincipal AccountPrincipal userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocument(userDetails.userId(), id));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<TransactionResponse>> listTransactions(
            @AuthenticationPrincipal AccountPrincipal userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(documentService.getTransactions(userDetails.userId(), id));
    }

    @GetMapping(value = "/{id}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> downloadOriginalFile(
            @AuthenticationPrincipal AccountPrincipal userDetails, @PathVariable Long id) {
        DownloadedFile file = documentService.downloadOriginalFile(userDetails.userId(), id);
        String disposition =
                ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file.content());
    }
}

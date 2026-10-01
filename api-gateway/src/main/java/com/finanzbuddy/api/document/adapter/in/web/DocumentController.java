package com.finanzbuddy.api.document.adapter.in.web;

import com.finanzbuddy.api.document.adapter.in.web.dto.DocumentListResponse;
import com.finanzbuddy.api.document.adapter.in.web.dto.DocumentResponse;
import com.finanzbuddy.api.document.adapter.in.web.dto.TransactionResponse;
import com.finanzbuddy.api.document.domain.model.Document;
import com.finanzbuddy.api.document.domain.model.DocumentType;
import com.finanzbuddy.api.document.domain.model.DownloadedFile;
import com.finanzbuddy.api.document.domain.model.PageRequest;
import com.finanzbuddy.api.document.domain.model.UploadDocumentCommand;
import com.finanzbuddy.api.document.port.in.DownloadOriginalFileUseCase;
import com.finanzbuddy.api.document.port.in.GetDocumentUseCase;
import com.finanzbuddy.api.document.port.in.ListDocumentsUseCase;
import com.finanzbuddy.api.document.port.in.ListTransactionsUseCase;
import com.finanzbuddy.api.document.port.in.UploadDocumentUseCase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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

    private final UploadDocumentUseCase uploadDocumentUseCase;
    private final ListDocumentsUseCase listDocumentsUseCase;
    private final GetDocumentUseCase getDocumentUseCase;
    private final ListTransactionsUseCase listTransactionsUseCase;
    private final DownloadOriginalFileUseCase downloadOriginalFileUseCase;

    public DocumentController(
            UploadDocumentUseCase uploadDocumentUseCase,
            ListDocumentsUseCase listDocumentsUseCase,
            GetDocumentUseCase getDocumentUseCase,
            ListTransactionsUseCase listTransactionsUseCase,
            DownloadOriginalFileUseCase downloadOriginalFileUseCase) {
        this.uploadDocumentUseCase = uploadDocumentUseCase;
        this.listDocumentsUseCase = listDocumentsUseCase;
        this.getDocumentUseCase = getDocumentUseCase;
        this.listTransactionsUseCase = listTransactionsUseCase;
        this.downloadOriginalFileUseCase = downloadOriginalFileUseCase;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> uploadDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) {
        DocumentType documentType = parseType(type);
        // MultipartFile streams are not guaranteed re-readable, so buffer the content once (bounded
        // by the multipart max-file-size) and hand the bytes to the domain for validation +
        // storage.
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unable to read uploaded file");
        }
        UploadDocumentCommand command =
                new UploadDocumentCommand(
                        content,
                        file.getOriginalFilename(),
                        file.getContentType(),
                        documentType,
                        userDetails.getUsername());
        Document document = uploadDocumentUseCase.upload(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DocumentWebMapper.toResponse(document));
    }

    @GetMapping
    public ResponseEntity<DocumentListResponse> listDocuments(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String filename,
            Pageable pageable) {
        PageRequest pageRequest =
                new PageRequest(pageable.getPageNumber(), pageable.getPageSize(), filename);
        return ResponseEntity.ok(
                DocumentWebMapper.toListResponse(
                        listDocumentsUseCase.list(userDetails.getUsername(), pageRequest)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(
                DocumentWebMapper.toResponse(
                        getDocumentUseCase.get(userDetails.getUsername(), id)));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<TransactionResponse>> listTransactions(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(
                DocumentWebMapper.toTransactionResponses(
                        listTransactionsUseCase.list(userDetails.getUsername(), id)));
    }

    @GetMapping(value = "/{id}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> downloadOriginalFile(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        DownloadedFile file = downloadOriginalFileUseCase.download(userDetails.getUsername(), id);
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

    private static DocumentType parseType(String type) {
        if (type == null || type.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document type is required");
        }
        try {
            return DocumentType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported document type: " + type);
        }
    }
}

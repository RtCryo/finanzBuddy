package com.finanzbuddy.api.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.finanzbuddy.api.document.dto.DocumentStatusUpdateRequest;
import com.finanzbuddy.api.entity.DocumentStatus;
import com.finanzbuddy.api.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DocumentRepository documentRepository;

    @InjectMocks private DocumentService documentService;

    @Test
    void updateDocumentStatusReturns409OnOptimisticLock() {
        when(documentRepository.findById(1L))
                .thenThrow(new OptimisticLockingFailureException("concurrent update"));

        DocumentStatusUpdateRequest request =
                new DocumentStatusUpdateRequest(DocumentStatus.PROCESSING, null);

        assertThatThrownBy(() -> documentService.updateDocumentStatus(1L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex -> {
                            ResponseStatusException rse = (ResponseStatusException) ex;
                            assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(rse.getReason()).contains("modified concurrently");
                        });
    }
}

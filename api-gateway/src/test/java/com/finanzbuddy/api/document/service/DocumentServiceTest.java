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

    @Mock private com.finanzbuddy.api.repository.UserRepository userRepository;

    @Mock
    private com.finanzbuddy.api.repository.TransactionReadRepository transactionReadRepository;

    @InjectMocks private DocumentService documentService;

    @Test
    void relatedExpensesResolveAuthenticatedOwnerAndGuardBeforeMatching() {
        var user = new com.finanzbuddy.api.entity.User();
        user.setId(7L);
        when(userRepository.findByEmail("owner@example.com"))
                .thenReturn(java.util.Optional.of(user));
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(transactionReadRepository.findOwnedProcessedSource(7L, 1L))
                .thenReturn(java.util.Optional.empty());
        assertThatThrownBy(
                        () -> documentService.getRelatedExpenses("owner@example.com", 1L, pageable))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));
        org.mockito.Mockito.verify(transactionReadRepository, org.mockito.Mockito.never())
                .findRelatedExpenses(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        var source =
                new com.finanzbuddy.api.document.dto.TransactionResponse(
                        1L, null, null, null, null, " \t", null, null);
        when(transactionReadRepository.findOwnedProcessedSource(7L, 1L))
                .thenReturn(java.util.Optional.of(source));
        when(transactionReadRepository.findRelatedExpenses(7L, 1L, " \t", pageable))
                .thenReturn(org.springframework.data.domain.Page.empty(pageable));
        var response = documentService.getRelatedExpenses("owner@example.com", 1L, pageable);
        assertThat(response.items()).isEmpty();
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
    }

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

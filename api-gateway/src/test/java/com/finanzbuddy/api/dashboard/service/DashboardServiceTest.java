package com.finanzbuddy.api.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finanzbuddy.api.dashboard.dto.DashboardResponse;
import com.finanzbuddy.api.entity.DocumentStatus;
import com.finanzbuddy.api.repository.DocumentRepository;
import com.finanzbuddy.api.repository.TransactionReadRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private TransactionReadRepository transactionReadRepository;

    @InjectMocks private DashboardService dashboardService;

    @Test
    void combinesOwnerScopedDocumentLifecycleAndTransactionMetrics() {
        when(documentRepository.countByUserId(42L)).thenReturn(5L);
        when(documentRepository.countByUserIdAndStatus(42L, DocumentStatus.PROCESSED))
                .thenReturn(2L);
        when(documentRepository.countByUserIdAndStatusIn(
                        42L, List.of(DocumentStatus.PENDING, DocumentStatus.PROCESSING)))
                .thenReturn(2L);
        when(transactionReadRepository.findDashboardMetrics(42L))
                .thenReturn(new TransactionReadRepository.DashboardTransactionMetrics(7, 3, 4));

        DashboardResponse response = dashboardService.getDashboard(42L);

        assertThat(response).isEqualTo(new DashboardResponse(5, 2, 2, 7, 3, 4));
        verify(documentRepository).countByUserId(42L);
        verify(documentRepository).countByUserIdAndStatus(42L, DocumentStatus.PROCESSED);
        verify(documentRepository)
                .countByUserIdAndStatusIn(
                        42L, List.of(DocumentStatus.PENDING, DocumentStatus.PROCESSING));
        verify(transactionReadRepository).findDashboardMetrics(42L);
    }
}

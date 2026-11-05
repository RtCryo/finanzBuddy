package com.finanzbuddy.api.dashboard.service;

import com.finanzbuddy.api.dashboard.dto.DashboardResponse;
import com.finanzbuddy.api.entity.DocumentStatus;
import com.finanzbuddy.api.repository.DocumentRepository;
import com.finanzbuddy.api.repository.TransactionReadRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final DocumentRepository documentRepository;
    private final TransactionReadRepository transactionReadRepository;

    public DashboardService(
            DocumentRepository documentRepository,
            TransactionReadRepository transactionReadRepository) {
        this.documentRepository = documentRepository;
        this.transactionReadRepository = transactionReadRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(long userId) {
        TransactionReadRepository.DashboardTransactionMetrics transactions =
                transactionReadRepository.findDashboardMetrics(userId);

        return new DashboardResponse(
                documentRepository.countByUserId(userId),
                documentRepository.countByUserIdAndStatus(userId, DocumentStatus.PROCESSED),
                documentRepository.countByUserIdAndStatusIn(
                        userId, List.of(DocumentStatus.PENDING, DocumentStatus.PROCESSING)),
                transactions.savedTransactions(),
                transactions.uniqueCategories(),
                transactions.uniqueCounterparties());
    }
}

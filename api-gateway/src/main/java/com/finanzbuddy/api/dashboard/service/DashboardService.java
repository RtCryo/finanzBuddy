package com.finanzbuddy.api.dashboard.service;

import com.finanzbuddy.api.dashboard.dto.DashboardResponse;
import com.finanzbuddy.api.entity.DocumentStatus;
import com.finanzbuddy.api.repository.DocumentRepository;
import com.finanzbuddy.api.repository.TransactionReadRepository;
import com.finanzbuddy.api.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardService {

    private final DocumentRepository documentRepository;
    private final TransactionReadRepository transactionReadRepository;
    private final UserRepository userRepository;

    public DashboardService(
            DocumentRepository documentRepository,
            TransactionReadRepository transactionReadRepository,
            UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.transactionReadRepository = transactionReadRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(String userEmail) {
        Long userId =
                userRepository
                        .findByEmail(userEmail)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "User not found"))
                        .getId();
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

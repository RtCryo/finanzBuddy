package com.finanzbuddy.api.cleanup.config;

import com.finanzbuddy.api.cleanup.domain.StalePendingReconciliation;
import com.finanzbuddy.api.cleanup.port.in.ReconcileStalePendingUseCase;
import com.finanzbuddy.api.cleanup.port.out.ClockPort;
import com.finanzbuddy.api.document.port.out.DocumentRepositoryPort;
import com.finanzbuddy.api.document.port.out.FileStoragePort;
import com.finanzbuddy.api.document.port.out.TransactionRunnerPort;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CleanupConfiguration {

    @Bean
    ReconcileStalePendingUseCase stalePendingReconciliation(
            DocumentRepositoryPort documentRepository,
            FileStoragePort fileStorage,
            TransactionRunnerPort transactionRunner,
            ClockPort clock,
            @Value("${app.reconciler.pending-timeout-ms:3600000}") long staleAfterMs,
            @Value("${app.reconciler.batch-size:500}") int batchSize) {
        return new StalePendingReconciliation(
                documentRepository,
                fileStorage,
                transactionRunner,
                clock,
                Duration.ofMillis(staleAfterMs),
                batchSize);
    }
}

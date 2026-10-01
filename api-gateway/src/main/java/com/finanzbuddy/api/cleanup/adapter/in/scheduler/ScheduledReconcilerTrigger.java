package com.finanzbuddy.api.cleanup.adapter.in.scheduler;

import com.finanzbuddy.api.cleanup.port.in.ReconcileStalePendingUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Spring trigger that drives one reconciliation pass on the configured schedule. */
@Component
public class ScheduledReconcilerTrigger {

    private final ReconcileStalePendingUseCase reconcileStalePending;

    public ScheduledReconcilerTrigger(ReconcileStalePendingUseCase reconcileStalePending) {
        this.reconcileStalePending = reconcileStalePending;
    }

    @Scheduled(fixedDelayString = "${app.reconciler.poll-interval-ms:900000}")
    public void cleanupStalePending() {
        reconcileStalePending.reconcile();
    }
}

package com.finanzbuddy.api.cleanup.port.in;

/** Runs one reconciliation pass over stale {@code PENDING} documents. */
public interface ReconcileStalePendingUseCase {

    void reconcile();
}

package com.finanzbuddy.parser.service;

import com.finanzbuddy.parser.client.GatewayStatusClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reports PROCESSED for documents whose transactions are already stored and remembers when the
 * gateway accepted it. A document whose report never arrived keeps its stored transactions, which
 * deduplication counts, so it is reported again the next time the same owner's document is handled.
 */
@Component
public class ProcessedStatusReporter {

    private static final Logger logger = LoggerFactory.getLogger(ProcessedStatusReporter.class);

    private final TransactionPersistenceService persistenceService;
    private final GatewayStatusClient gatewayStatusClient;

    public ProcessedStatusReporter(
            TransactionPersistenceService persistenceService,
            GatewayStatusClient gatewayStatusClient) {
        this.persistenceService = persistenceService;
        this.gatewayStatusClient = gatewayStatusClient;
    }

    /** Reports PROCESSED with the given counts (both null for historic documents). */
    public void report(Long documentId, Integer saved, Integer skipped) {
        gatewayStatusClient.reportProcessed(documentId, saved, skipped);
        persistenceService.markStatusConfirmed(documentId);
    }

    /** Reports PROCESSED with the counts stored for the document. */
    public void reportStored(Long documentId) {
        PersistenceOutcome outcome = persistenceService.findOutcome(documentId).orElse(null);
        report(
                documentId,
                outcome == null ? null : outcome.saved(),
                outcome == null ? null : outcome.skipped());
    }

    /**
     * Best-effort recovery: reports the owner's other documents that were saved but never
     * confirmed. A failure is logged and leaves the document pending for the next attempt.
     */
    public void retryUnconfirmed(Long userId, Long currentDocumentId) {
        try {
            for (Long id : persistenceService.findUnconfirmedDocuments(userId, currentDocumentId)) {
                try {
                    reportStored(id);
                    logger.info("Recovered unconfirmed PROCESSED status: documentId={}", id);
                } catch (Exception e) {
                    logger.warn("PROCESSED status still unconfirmed: documentId={}", id, e);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not look up unconfirmed documents for userId={}", userId, e);
        }
    }
}

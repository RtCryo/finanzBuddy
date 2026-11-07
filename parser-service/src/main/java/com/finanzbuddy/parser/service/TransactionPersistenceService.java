package com.finanzbuddy.parser.service;

import com.finanzbuddy.parser.entity.ProcessedDocument;
import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.repository.ProcessedDocumentRepository;
import com.finanzbuddy.parser.repository.StoredTransactionKey;
import com.finanzbuddy.parser.repository.TransactionRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionPersistenceService {

    private final ProcessedDocumentRepository processedDocumentRepository;
    private final TransactionRepository transactionRepository;
    private final OwnerLock ownerLock;

    public TransactionPersistenceService(
            ProcessedDocumentRepository processedDocumentRepository,
            TransactionRepository transactionRepository,
            OwnerLock ownerLock) {
        this.processedDocumentRepository = processedDocumentRepository;
        this.transactionRepository = transactionRepository;
        this.ownerLock = ownerLock;
    }

    public boolean isProcessed(Long documentId) {
        return processedDocumentRepository.existsById(documentId);
    }

    /**
     * The recorded outcome of a processed document. Empty when the document is unknown or was
     * processed before outcomes were recorded.
     */
    @Transactional(readOnly = true)
    public Optional<PersistenceOutcome> findOutcome(Long documentId) {
        return processedDocumentRepository
                .findById(documentId)
                .filter(d -> d.getSavedCount() != null && d.getSkippedCount() != null)
                .map(d -> new PersistenceOutcome(d.getSavedCount(), d.getSkippedCount()));
    }

    /** Records that the gateway accepted the PROCESSED status of the document. */
    @Transactional
    public void markStatusConfirmed(Long documentId) {
        processedDocumentRepository.markConfirmed(documentId);
    }

    /**
     * Ids of the owner's documents that were saved but whose PROCESSED status never reached the
     * gateway, oldest first.
     */
    @Transactional(readOnly = true)
    public List<Long> findUnconfirmedDocuments(Long userId, Long excludingDocumentId) {
        return processedDocumentRepository
                .findUnconfirmedIds(userId)
                .stream()
                .filter(id -> !id.equals(excludingDocumentId))
                .toList();
    }

    /**
     * Saves the transactions the owner does not have yet. Identical transactions are compared by
     * quantity: only the surplus of an identity key over the stored count is saved, taking the
     * first lines of the file in order and skipping the rest.
     */
    @Transactional
    public PersistenceOutcome saveIfNotProcessed(
            Long documentId, Long userId, List<Transaction> transactions) {
        ownerLock.acquire(userId);
        // Checked under the owner lock: saveAndFlush merges an existing id instead of failing, which
        // would overwrite the recorded outcome of an already processed document.
        if (processedDocumentRepository.existsById(documentId)) {
            throw new DuplicateDocumentException(documentId);
        }
        ProcessedDocument processed;
        try {
            processed =
                    processedDocumentRepository.saveAndFlush(
                            new ProcessedDocument(documentId, userId));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateDocumentException(documentId);
        }

        Map<StoredTransactionKey, Integer> stored = storedCounts(userId, transactions);
        List<Transaction> toSave = new ArrayList<>();
        Map<StoredTransactionKey, Integer> seen = new HashMap<>();
        for (Transaction transaction : transactions) {
            StoredTransactionKey key = StoredTransactionKey.of(transaction);
            int occurrence = seen.merge(key, 1, Integer::sum);
            if (occurrence > stored.getOrDefault(key, 0)) {
                toSave.add(transaction);
            }
        }

        transactionRepository.saveAll(toSave);
        PersistenceOutcome outcome =
                new PersistenceOutcome(toSave.size(), transactions.size() - toSave.size());
        processed.recordOutcome(outcome.saved(), outcome.skipped());
        return outcome;
    }

    private Map<StoredTransactionKey, Integer> storedCounts(
            Long userId, List<Transaction> transactions) {
        LocalDate from = null;
        LocalDate to = null;
        for (Transaction transaction : transactions) {
            LocalDate date = transaction.getBookingDate();
            if (date == null) {
                continue;
            }
            if (from == null || date.isBefore(from)) {
                from = date;
            }
            if (to == null || date.isAfter(to)) {
                to = date;
            }
        }
        List<StoredTransactionKey> keys =
                from == null
                        ? transactionRepository.findKeysWithoutBookingDate(userId)
                        : transactionRepository.findKeysInBookingRange(userId, from, to);
        Map<StoredTransactionKey, Integer> counts = new HashMap<>();
        for (StoredTransactionKey key : keys) {
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }
}

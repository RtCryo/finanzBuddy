package com.finanzbuddy.parser.service;

import com.finanzbuddy.parser.entity.ProcessedDocument;
import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.repository.ProcessedDocumentRepository;
import com.finanzbuddy.parser.repository.TransactionRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionPersistenceService {

    private final ProcessedDocumentRepository processedDocumentRepository;
    private final TransactionRepository transactionRepository;

    public TransactionPersistenceService(
            ProcessedDocumentRepository processedDocumentRepository,
            TransactionRepository transactionRepository) {
        this.processedDocumentRepository = processedDocumentRepository;
        this.transactionRepository = transactionRepository;
    }

    public boolean isProcessed(Long documentId) {
        return processedDocumentRepository.existsById(documentId);
    }

    @Transactional
    public boolean saveIfNotProcessed(Long documentId, List<Transaction> transactions) {
        try {
            processedDocumentRepository.saveAndFlush(new ProcessedDocument(documentId));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateDocumentException(documentId);
        }
        transactionRepository.saveAll(transactions);
        return true;
    }
}

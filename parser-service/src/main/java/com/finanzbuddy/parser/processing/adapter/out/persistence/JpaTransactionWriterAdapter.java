package com.finanzbuddy.parser.processing.adapter.out.persistence;

import com.finanzbuddy.parser.parsing.domain.model.Transaction;
import com.finanzbuddy.parser.processing.domain.exception.TransactionPersistenceException;
import com.finanzbuddy.parser.processing.domain.model.WriteResult;
import com.finanzbuddy.parser.processing.port.out.TransactionWriterPort;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaTransactionWriterAdapter implements TransactionWriterPort {

    private final ProcessedDocumentRepository processedDocumentRepository;
    private final TransactionRepository transactionRepository;

    public JpaTransactionWriterAdapter(
            ProcessedDocumentRepository processedDocumentRepository,
            TransactionRepository transactionRepository) {
        this.processedDocumentRepository = processedDocumentRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public boolean isAlreadyProcessed(Long documentId) {
        return processedDocumentRepository.existsById(documentId);
    }

    @Override
    @Transactional
    public WriteResult writeIfNotProcessed(Long documentId, List<Transaction> transactions) {
        try {
            processedDocumentRepository.saveAndFlush(new ProcessedDocument(documentId));
        } catch (DataIntegrityViolationException e) {
            return WriteResult.DUPLICATE;
        }
        try {
            transactionRepository.saveAll(transactions.stream().map(this::toEntity).toList());
        } catch (DataAccessException e) {
            throw new TransactionPersistenceException(
                    "Failed to persist transactions for document " + documentId, e);
        }
        return WriteResult.WRITTEN;
    }

    private TransactionEntity toEntity(Transaction source) {
        TransactionEntity entity = new TransactionEntity();
        entity.setDocumentId(source.documentId());
        entity.setUserId(source.userId());
        entity.setBookingDate(source.bookingDate());
        entity.setValueDate(source.valueDate());
        entity.setAmount(source.amount());
        entity.setCurrency(source.currency());
        entity.setCounterparty(source.counterparty());
        entity.setDescription(source.description());
        entity.setCategory(source.category());
        entity.setRawData(source.rawData());
        return entity;
    }
}

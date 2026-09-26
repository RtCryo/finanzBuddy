package com.finanzbuddy.parser.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finanzbuddy.parser.entity.ProcessedDocument;
import com.finanzbuddy.parser.repository.ProcessedDocumentRepository;
import com.finanzbuddy.parser.repository.TransactionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class TransactionPersistenceServiceTest {

    private final ProcessedDocumentRepository processedDocumentRepository =
            mock(ProcessedDocumentRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final TransactionPersistenceService service =
            new TransactionPersistenceService(processedDocumentRepository, transactionRepository);

    @Test
    void duplicateKeyMeansDocumentWasAlreadyProcessed() {
        when(processedDocumentRepository.saveAndFlush(any(ProcessedDocument.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.saveIfNotProcessed(10L, List.of()))
                .isInstanceOf(DuplicateDocumentException.class)
                .hasMessageContaining("10");

        verify(transactionRepository, never()).saveAll(any());
    }
}

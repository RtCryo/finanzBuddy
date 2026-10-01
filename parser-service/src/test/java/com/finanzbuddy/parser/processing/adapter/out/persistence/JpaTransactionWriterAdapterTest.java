package com.finanzbuddy.parser.processing.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finanzbuddy.parser.processing.domain.model.WriteResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class JpaTransactionWriterAdapterTest {

    private final ProcessedDocumentRepository processedDocumentRepository =
            mock(ProcessedDocumentRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final JpaTransactionWriterAdapter adapter =
            new JpaTransactionWriterAdapter(processedDocumentRepository, transactionRepository);

    @Test
    void duplicateKeyMeansDocumentWasAlreadyProcessed() {
        when(processedDocumentRepository.saveAndFlush(any(ProcessedDocument.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        WriteResult result = adapter.writeIfNotProcessed(10L, List.of());

        assertThat(result).isEqualTo(WriteResult.DUPLICATE);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void writesTransactionsWhenNotYetProcessed() {
        WriteResult result = adapter.writeIfNotProcessed(10L, List.of());

        assertThat(result).isEqualTo(WriteResult.WRITTEN);
        verify(transactionRepository).saveAll(any());
    }
}

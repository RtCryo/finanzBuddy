package com.finanzbuddy.parser.processing.port.out;

import com.finanzbuddy.parser.parsing.domain.model.Transaction;
import com.finanzbuddy.parser.processing.domain.model.WriteResult;
import java.util.List;

/** Outbound port: idempotent persistence of extracted transactions. */
public interface TransactionWriterPort {

    boolean isAlreadyProcessed(Long documentId);

    /** Records the document as processed and inserts its transactions atomically. */
    WriteResult writeIfNotProcessed(Long documentId, List<Transaction> transactions);
}

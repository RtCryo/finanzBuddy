package com.finanzbuddy.api.document.port.out;

import com.finanzbuddy.api.document.domain.model.Transaction;
import java.util.List;

public interface TransactionReadPort {

    List<Transaction> findByDocumentId(long documentId);
}

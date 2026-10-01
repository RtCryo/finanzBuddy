package com.finanzbuddy.api.document.port.in;

import com.finanzbuddy.api.document.domain.model.Transaction;
import java.util.List;

public interface ListTransactionsUseCase {

    List<Transaction> list(String ownerEmail, long documentId);
}

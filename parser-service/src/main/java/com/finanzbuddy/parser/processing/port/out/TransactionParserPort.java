package com.finanzbuddy.parser.processing.port.out;

import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parsing.domain.model.Transaction;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/** Outbound port: route the document to a parser and extract domain transactions. */
public interface TransactionParserPort {

    List<Transaction> parse(InputStream content, DocumentUploadedEvent event, Locale locale);
}

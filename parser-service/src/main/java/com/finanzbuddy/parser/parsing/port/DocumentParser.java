package com.finanzbuddy.parser.parsing.port;

import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parsing.domain.model.Transaction;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public interface DocumentParser {

    boolean supports(String documentType);

    List<Transaction> parse(InputStream inputStream, DocumentUploadedEvent event, Locale locale);
}

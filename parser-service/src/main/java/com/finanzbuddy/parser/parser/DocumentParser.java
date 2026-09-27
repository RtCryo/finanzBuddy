package com.finanzbuddy.parser.parser;

import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public interface DocumentParser {

    boolean supports(String documentType);

    List<Transaction> parse(InputStream inputStream, DocumentUploadedEvent event, Locale locale);
}

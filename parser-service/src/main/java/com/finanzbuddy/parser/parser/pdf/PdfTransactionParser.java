package com.finanzbuddy.parser.parser.pdf;

import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parser.DocumentParser;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PdfTransactionParser implements DocumentParser {

    @Override
    public boolean supports(String documentType) {
        return "PDF".equalsIgnoreCase(documentType);
    }

    @Override
    public List<Transaction> parse(InputStream inputStream, DocumentUploadedEvent event, Locale locale) {
        throw new UnsupportedOperationException("PDF parsing is not implemented in the MVP");
    }
}

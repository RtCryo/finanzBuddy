package com.finanzbuddy.parser.parsing.adapter.pdf;

import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parsing.domain.model.Transaction;
import com.finanzbuddy.parser.parsing.port.DocumentParser;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PdfParserAdapter implements DocumentParser {

    @Override
    public boolean supports(String documentType) {
        return "PDF".equalsIgnoreCase(documentType);
    }

    @Override
    public List<Transaction> parse(
            InputStream inputStream, DocumentUploadedEvent event, Locale locale) {
        throw new UnsupportedOperationException("PDF parsing is not implemented in the MVP");
    }
}

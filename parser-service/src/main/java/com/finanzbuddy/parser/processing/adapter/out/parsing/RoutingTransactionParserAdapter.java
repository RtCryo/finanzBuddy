package com.finanzbuddy.parser.processing.adapter.out.parsing;

import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parsing.domain.DocumentParserRouter;
import com.finanzbuddy.parser.parsing.domain.model.Transaction;
import com.finanzbuddy.parser.parsing.port.DocumentParser;
import com.finanzbuddy.parser.processing.port.out.TransactionParserPort;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Bridges the processing slice to the parsing slice via its router. */
@Component
public class RoutingTransactionParserAdapter implements TransactionParserPort {

    private final DocumentParserRouter parserRouter;

    public RoutingTransactionParserAdapter(DocumentParserRouter parserRouter) {
        this.parserRouter = parserRouter;
    }

    @Override
    public List<Transaction> parse(
            InputStream content, DocumentUploadedEvent event, Locale locale) {
        DocumentParser parser = parserRouter.route(event.type());
        return parser.parse(content, event, locale);
    }
}

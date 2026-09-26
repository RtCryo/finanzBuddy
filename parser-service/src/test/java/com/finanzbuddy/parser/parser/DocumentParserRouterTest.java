package com.finanzbuddy.parser.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class DocumentParserRouterTest {

    private final DocumentParser csvParser =
            new DocumentParser() {
                @Override
                public boolean supports(String documentType) {
                    return "CSV".equalsIgnoreCase(documentType);
                }

                @Override
                public List<Transaction> parse(
                        InputStream inputStream, DocumentUploadedEvent event, Locale locale) {
                    return List.of();
                }
            };

    private final DocumentParserRouter router = new DocumentParserRouter(List.of(csvParser));

    @Test
    void routesToSupportingParser() {
        DocumentParser parser = router.route("CSV");

        assertThat(parser).isSameAs(csvParser);
    }

    @Test
    void throwsWhenNoParserSupportsType() {
        assertThatThrownBy(() -> router.route("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No parser registered for document type: UNKNOWN");
    }
}

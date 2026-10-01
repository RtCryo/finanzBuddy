package com.finanzbuddy.parser.parsing.adapter.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PdfParserAdapterTest {

    private final PdfParserAdapter parser = new PdfParserAdapter();

    @Test
    void supportsOnlyPdfType() {
        assertThat(parser.supports("PDF")).isTrue();
        assertThat(parser.supports("pdf")).isTrue();
        assertThat(parser.supports("CSV")).isFalse();
    }

    @Test
    void parseThrowsUnsupportedOperationException() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "PDF", "pdf/1");

        assertThatThrownBy(
                        () ->
                                parser.parse(
                                        new ByteArrayInputStream(new byte[0]),
                                        event,
                                        Locale.GERMANY))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("PDF parsing is not implemented");
    }
}

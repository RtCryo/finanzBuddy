package com.finanzbuddy.parser.parsing.adapter.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class XmlParserAdapterTest {

    private final XmlParserAdapter parser = new XmlParserAdapter();

    @Test
    void supportsOnlyXmlType() {
        assertThat(parser.supports("XML")).isTrue();
        assertThat(parser.supports("xml")).isTrue();
        assertThat(parser.supports("CSV")).isFalse();
    }

    @Test
    void parseThrowsUnsupportedOperationException() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "XML", "xml/1");

        assertThatThrownBy(
                        () ->
                                parser.parse(
                                        new ByteArrayInputStream(new byte[0]),
                                        event,
                                        Locale.GERMANY))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("XML parsing is not implemented");
    }
}

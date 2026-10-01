package com.finanzbuddy.parser.parsing.domain;

import com.finanzbuddy.parser.parsing.port.DocumentParser;
import java.util.List;

public class DocumentParserRouter {

    private final List<DocumentParser> parsers;

    public DocumentParserRouter(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    public DocumentParser route(String documentType) {
        return parsers.stream()
                .filter(parser -> parser.supports(documentType))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "No parser registered for document type: " + documentType));
    }
}

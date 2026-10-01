package com.finanzbuddy.parser.parsing.config;

import com.finanzbuddy.parser.parsing.domain.DocumentParserRouter;
import com.finanzbuddy.parser.parsing.port.DocumentParser;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ParsingConfiguration {

    @Bean
    DocumentParserRouter documentParserRouter(List<DocumentParser> parsers) {
        return new DocumentParserRouter(parsers);
    }
}

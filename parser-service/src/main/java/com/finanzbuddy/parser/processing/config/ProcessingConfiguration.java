package com.finanzbuddy.parser.processing.config;

import com.finanzbuddy.parser.processing.domain.DocumentProcessor;
import com.finanzbuddy.parser.processing.port.out.FileFetchPort;
import com.finanzbuddy.parser.processing.port.out.ProcessingMetricsPort;
import com.finanzbuddy.parser.processing.port.out.StatusReporterPort;
import com.finanzbuddy.parser.processing.port.out.TransactionParserPort;
import com.finanzbuddy.parser.processing.port.out.TransactionWriterPort;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProcessingConfiguration {

    @Bean
    DocumentProcessor documentProcessor(
            FileFetchPort fileFetch,
            TransactionParserPort transactionParser,
            TransactionWriterPort transactionWriter,
            StatusReporterPort statusReporter,
            ProcessingMetricsPort metrics,
            @Value("${parser.formats.csv-locale:de-DE}") Locale defaultLocale) {
        return new DocumentProcessor(
                fileFetch,
                transactionParser,
                transactionWriter,
                statusReporter,
                metrics,
                defaultLocale);
    }
}

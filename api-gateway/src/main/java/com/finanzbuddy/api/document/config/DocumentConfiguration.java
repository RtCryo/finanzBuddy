package com.finanzbuddy.api.document.config;

import com.finanzbuddy.api.document.domain.DocumentService;
import com.finanzbuddy.api.document.domain.model.DocumentType;
import com.finanzbuddy.api.document.port.out.DocumentEventPort;
import com.finanzbuddy.api.document.port.out.DocumentRepositoryPort;
import com.finanzbuddy.api.document.port.out.FileStoragePort;
import com.finanzbuddy.api.document.port.out.TransactionReadPort;
import com.finanzbuddy.api.document.port.out.TransactionRunnerPort;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentConfiguration {

    @Bean
    DocumentService documentService(
            DocumentRepositoryPort documentRepository,
            TransactionReadPort transactionRead,
            FileStoragePort fileStorage,
            DocumentEventPort documentEvent,
            TransactionRunnerPort transactionRunner,
            @Value("${app.upload.accepted-types:CSV}") Set<DocumentType> acceptedTypes) {
        return new DocumentService(
                documentRepository,
                transactionRead,
                fileStorage,
                documentEvent,
                transactionRunner,
                acceptedTypes);
    }
}

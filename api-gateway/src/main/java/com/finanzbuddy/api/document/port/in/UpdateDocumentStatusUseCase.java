package com.finanzbuddy.api.document.port.in;

import com.finanzbuddy.api.document.domain.model.Document;
import com.finanzbuddy.api.document.domain.model.DocumentStatus;

public interface UpdateDocumentStatusUseCase {

    Document updateStatus(long documentId, DocumentStatus newStatus, String message);
}

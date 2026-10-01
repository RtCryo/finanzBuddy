package com.finanzbuddy.api.document.port.in;

import com.finanzbuddy.api.document.domain.model.Document;

public interface GetDocumentUseCase {

    Document get(String ownerEmail, long documentId);
}

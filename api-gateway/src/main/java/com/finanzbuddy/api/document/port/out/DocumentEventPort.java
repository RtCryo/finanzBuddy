package com.finanzbuddy.api.document.port.out;

import com.finanzbuddy.api.document.domain.model.Document;

public interface DocumentEventPort {

    /** Enqueues a document-uploaded event in the current transaction (transactional outbox). */
    void enqueueUploadedEvent(Document document);
}

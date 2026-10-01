package com.finanzbuddy.api.document.port.in;

import com.finanzbuddy.api.document.domain.model.DocumentListResult;
import com.finanzbuddy.api.document.domain.model.PageRequest;

public interface ListDocumentsUseCase {

    DocumentListResult list(String ownerEmail, PageRequest pageRequest);
}

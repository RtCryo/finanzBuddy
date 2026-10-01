package com.finanzbuddy.api.document.port.in;

import com.finanzbuddy.api.document.domain.model.Document;
import com.finanzbuddy.api.document.domain.model.UploadDocumentCommand;

public interface UploadDocumentUseCase {

    Document upload(UploadDocumentCommand command);
}

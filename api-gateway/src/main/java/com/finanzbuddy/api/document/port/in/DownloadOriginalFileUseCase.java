package com.finanzbuddy.api.document.port.in;

import com.finanzbuddy.api.document.domain.model.DownloadedFile;

public interface DownloadOriginalFileUseCase {

    DownloadedFile download(String ownerEmail, long documentId);
}

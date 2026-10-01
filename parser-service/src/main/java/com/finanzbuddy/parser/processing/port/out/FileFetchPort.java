package com.finanzbuddy.parser.processing.port.out;

import java.io.InputStream;

/** Outbound port: fetch the stored object by key. */
public interface FileFetchPort {

    /**
     * Opens a stream to the stored object. Throws {@link
     * com.finanzbuddy.parser.processing.domain.exception.FileFetchException} on failure.
     */
    InputStream fetch(String storagePath);
}

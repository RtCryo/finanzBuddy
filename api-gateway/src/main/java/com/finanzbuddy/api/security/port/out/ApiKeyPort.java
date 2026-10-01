package com.finanzbuddy.api.security.port.out;

public interface ApiKeyPort {

    /** Constant-time comparison of the provided key against the configured internal api-key. */
    boolean matches(String provided);
}

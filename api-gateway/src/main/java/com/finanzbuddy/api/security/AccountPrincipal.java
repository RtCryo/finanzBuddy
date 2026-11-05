package com.finanzbuddy.api.security;

import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Authenticated homeBuddy account, resolved once per request to its local {@code users} row.
 * Controllers use {@link #userId()} for ownership checks; the email is informational only.
 */
public record AccountPrincipal(long userId, UUID accountId, String email)
        implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return accountId.toString();
    }
}

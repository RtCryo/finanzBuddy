package com.finanzbuddy.api.security.port.in;

import com.finanzbuddy.api.security.domain.model.Principal;
import java.util.Optional;

public interface AuthenticateInternalUseCase {

    /** Resolves the service principal when the supplied internal api-key matches, else empty. */
    Optional<Principal> authenticate(String apiKey);
}

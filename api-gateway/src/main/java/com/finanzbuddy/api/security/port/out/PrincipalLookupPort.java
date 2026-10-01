package com.finanzbuddy.api.security.port.out;

import com.finanzbuddy.api.security.domain.model.Principal;
import java.util.Optional;

public interface PrincipalLookupPort {

    /** Loads the principal for a token subject from the shared user source. */
    Optional<Principal> findBySubject(String subject);
}

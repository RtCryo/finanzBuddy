package com.finanzbuddy.api.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.finanzbuddy.api.security.domain.model.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InternalAuthenticatorTest {

    @Test
    void grantsServicePrincipalWhenKeyMatches() {
        InternalAuthenticator authenticator = new InternalAuthenticator(provided -> true);

        Optional<Principal> principal = authenticator.authenticate("correct-key");

        assertThat(principal).contains(new Principal("internal-service", List.of("ROLE_SERVICE")));
    }

    @Test
    void returnsEmptyWhenKeyDoesNotMatch() {
        InternalAuthenticator authenticator = new InternalAuthenticator(provided -> false);

        assertThat(authenticator.authenticate("wrong-key")).isEmpty();
    }
}

package com.finanzbuddy.api.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finanzbuddy.api.security.domain.exception.ExpiredTokenException;
import com.finanzbuddy.api.security.domain.exception.InvalidTokenException;
import com.finanzbuddy.api.security.domain.model.Principal;
import com.finanzbuddy.api.security.domain.model.TokenValidationResult;
import com.finanzbuddy.api.security.port.out.AccessTokenPort;
import com.finanzbuddy.api.security.port.out.PrincipalLookupPort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RequestAuthenticatorTest {

    @Test
    void resolvesPrincipalForValidToken() {
        RequestAuthenticator authenticator =
                new RequestAuthenticator(
                        tokenReturning(TokenValidationResult.valid("user@example.com")),
                        subject ->
                                Optional.of(
                                        new Principal("user@example.com", List.of("ROLE_USER"))));

        Optional<Principal> principal = authenticator.authenticate("token");

        assertThat(principal).contains(new Principal("user@example.com", List.of("ROLE_USER")));
    }

    @Test
    void throwsExpiredWithSubjectForExpiredToken() {
        RequestAuthenticator authenticator =
                new RequestAuthenticator(
                        tokenReturning(TokenValidationResult.expired("user@example.com")),
                        failingLookup());

        assertThatThrownBy(() -> authenticator.authenticate("token"))
                .isInstanceOf(ExpiredTokenException.class)
                .extracting(e -> ((ExpiredTokenException) e).subject())
                .isEqualTo("user@example.com");
    }

    @Test
    void throwsInvalidForMalformedToken() {
        RequestAuthenticator authenticator =
                new RequestAuthenticator(
                        tokenReturning(TokenValidationResult.invalid()), failingLookup());

        assertThatThrownBy(() -> authenticator.authenticate("token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void returnsEmptyWhenSubjectResolvesToNoUser() {
        RequestAuthenticator authenticator =
                new RequestAuthenticator(
                        tokenReturning(TokenValidationResult.valid("ghost@example.com")),
                        subject -> Optional.empty());

        assertThat(authenticator.authenticate("token")).isEmpty();
    }

    @Test
    void returnsEmptyWhenResolvedUsernameDiffersFromSubject() {
        RequestAuthenticator authenticator =
                new RequestAuthenticator(
                        tokenReturning(TokenValidationResult.valid("subject@example.com")),
                        subject ->
                                Optional.of(
                                        new Principal(
                                                "different@example.com", List.of("ROLE_USER"))));

        assertThat(authenticator.authenticate("token")).isEmpty();
    }

    private static AccessTokenPort tokenReturning(TokenValidationResult result) {
        return new AccessTokenPort() {
            @Override
            public String issue(String subject) {
                throw new AssertionError("issue must not run during request authentication");
            }

            @Override
            public TokenValidationResult validate(String token) {
                return result;
            }
        };
    }

    private static PrincipalLookupPort failingLookup() {
        return subject -> {
            throw new AssertionError("principal lookup must not run for an unusable token");
        };
    }
}

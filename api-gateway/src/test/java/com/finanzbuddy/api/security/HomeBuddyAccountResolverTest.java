package com.finanzbuddy.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finanzbuddy.api.entity.User;
import com.finanzbuddy.api.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class HomeBuddyAccountResolverTest {

    @Autowired private HomeBuddyAccountResolver resolver;
    @Autowired private UserRepository users;

    @Test
    void linksLegacyUserWithoutChangingItsLocalIdOrPassword() {
        String email = "legacy-" + UUID.randomUUID() + "@example.com";
        User legacy = new User();
        legacy.setEmail(email);
        legacy.setPasswordHash("legacy-hash");
        Long localId = users.saveAndFlush(legacy).getId();
        UUID accountId = UUID.randomUUID();

        User linked = resolver.resolve(token(accountId, email.toUpperCase()));

        assertThat(linked.getId()).isEqualTo(localId);
        assertThat(linked.getAccountId()).isEqualTo(accountId);
        assertThat(linked.getPasswordHash()).isEqualTo("legacy-hash");
    }

    @Test
    void createsNewUserWithoutLocalPasswordAndKeepsIdOnEmailChange() {
        UUID accountId = UUID.randomUUID();
        String oldEmail = "new-" + accountId + "@example.com";
        User created = resolver.resolve(token(accountId, oldEmail));
        String newEmail = "renamed-" + accountId + "@example.com";

        User renamed = resolver.resolve(token(accountId, newEmail));

        assertThat(created.getPasswordHash()).isNull();
        assertThat(renamed.getId()).isEqualTo(created.getId());
        assertThat(renamed.getEmail()).isEqualTo(newEmail);
    }

    @Test
    void rejectsASecondAccountClaimingAnAlreadyLinkedEmail() {
        String email = "owned-" + UUID.randomUUID() + "@example.com";
        resolver.resolve(token(UUID.randomUUID(), email));

        assertThatThrownBy(() -> resolver.resolve(token(UUID.randomUUID(), email)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void concurrentFirstRequestsResolveToOneLocalUser() throws Exception {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = token(accountId, "concurrent-" + accountId + "@example.com");
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first =
                    workers.submit(
                            () -> {
                                start.await();
                                return resolver.resolve(jwt).getId();
                            });
            var second =
                    workers.submit(
                            () -> {
                                start.await();
                                return resolver.resolve(jwt).getId();
                            });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
            assertThat(users.findByAccountId(accountId)).isPresent();
        }
    }

    private Jwt token(UUID subject, String email) {
        return new Jwt(
                "test-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256", "kid", "test"),
                Map.of(
                        "sub",
                        subject.toString(),
                        "email",
                        email,
                        "iss",
                        "homebuddy-auth",
                        "aud",
                        List.of("finanzbuddy")));
    }
}

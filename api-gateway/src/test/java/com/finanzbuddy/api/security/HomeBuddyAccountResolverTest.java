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
    void resolvesLegacyUserLinkedByTheOperatorWithoutChangingItsIdOrPassword() {
        UUID accountId = UUID.randomUUID();
        String email = "legacy-" + accountId + "@example.com";
        User legacy = legacy(email);
        legacy.setAccountId(accountId);
        Long localId = users.saveAndFlush(legacy).getId();

        User linked = resolver.resolve(token(accountId, email));

        assertThat(linked.getId()).isEqualTo(localId);
        assertThat(linked.getPasswordHash()).isEqualTo("legacy-hash");
    }

    @Test
    void neverClaimsAnUnlinkedLegacyUserByEmail() {
        String email = "unlinked-" + UUID.randomUUID() + "@example.com";
        Long localId = users.saveAndFlush(legacy(email)).getId();
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() -> resolver.resolve(token(stranger, email.toUpperCase())))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(users.findById(localId).orElseThrow().getAccountId()).isNull();
        assertThat(users.findByAccountId(stranger)).isEmpty();
    }

    @Test
    void rejectsCaseVariantLegacyDuplicatesWithoutAServerError() {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        users.saveAndFlush(legacy(email));
        users.saveAndFlush(legacy(email.toUpperCase()));

        assertThatThrownBy(() -> resolver.resolve(token(UUID.randomUUID(), email)))
                .isInstanceOf(BadCredentialsException.class);
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
    void emailChangeToAnAddressStillStoredOnAnotherAccountKeepsBothAccountsWorking() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String released = "released-" + first + "@example.com";
        User firstUser = resolver.resolve(token(first, released));
        User secondUser = resolver.resolve(token(second, "other-" + second + "@example.com"));

        // The first account gave the address up in authBuddy; the second one took it over.
        User moved = resolver.resolve(token(second, released));

        assertThat(moved.getId()).isEqualTo(secondUser.getId());
        assertThat(moved.getEmail()).isEqualTo(released);
        assertThat(resolver.resolve(token(first, "new-" + first + "@example.com")).getId())
                .isEqualTo(firstUser.getId());
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

    private User legacy(String email) {
        User legacy = new User();
        legacy.setEmail(email);
        legacy.setPasswordHash("legacy-hash");
        return legacy;
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

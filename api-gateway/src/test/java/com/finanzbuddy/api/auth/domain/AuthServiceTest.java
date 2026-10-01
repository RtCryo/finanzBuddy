package com.finanzbuddy.api.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.finanzbuddy.api.auth.domain.exception.EmailAlreadyRegisteredException;
import com.finanzbuddy.api.auth.domain.exception.InvalidCredentialsException;
import com.finanzbuddy.api.auth.domain.model.AccessToken;
import com.finanzbuddy.api.auth.domain.model.AuthenticatedUser;
import com.finanzbuddy.api.auth.domain.model.Credentials;
import com.finanzbuddy.api.auth.domain.model.Registration;
import com.finanzbuddy.api.auth.domain.model.UserAccount;
import com.finanzbuddy.api.auth.port.out.AccessTokenIssuerPort;
import com.finanzbuddy.api.auth.port.out.CredentialAuthenticatorPort;
import com.finanzbuddy.api.auth.port.out.PasswordHasherPort;
import com.finanzbuddy.api.auth.port.out.TransactionRunnerPort;
import com.finanzbuddy.api.auth.port.out.UserRepositoryPort;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final PasswordHasherPort passwordHasher = raw -> "hash(" + raw + ")";
    private final RecordingAuthenticator authenticator = new RecordingAuthenticator();
    private final AccessTokenIssuerPort tokenIssuer =
            user -> new AccessToken("token:" + user.email());
    private final TransactionRunnerPort transactionRunner = Supplier::get;

    private final AuthService authService =
            new AuthService(
                    userRepository, passwordHasher, authenticator, tokenIssuer, transactionRunner);

    @Test
    void registerNormalizesEmailHashesPasswordAndIssuesToken() {
        AccessToken token = authService.register(new Registration("  User@Example.COM ", "secret"));

        assertThat(token.value()).isEqualTo("token:user@example.com");
        assertThat(userRepository.accounts).containsKey("user@example.com");
        assertThat(userRepository.accounts.get("user@example.com").passwordHash())
                .isEqualTo("hash(secret)");
    }

    @Test
    void registerRejectsExistingEmail() {
        userRepository.accounts.put(
                "taken@example.com", new UserAccount("taken@example.com", "hash"));

        assertThatExceptionOfType(EmailAlreadyRegisteredException.class)
                .isThrownBy(() -> authService.register(new Registration("taken@example.com", "x")));
    }

    @Test
    void registerPropagatesPersistenceDuplicate() {
        userRepository.failCreateWithDuplicate = true;

        assertThatExceptionOfType(EmailAlreadyRegisteredException.class)
                .isThrownBy(() -> authService.register(new Registration("race@example.com", "x")));
    }

    @Test
    void loginIssuesTokenForAuthenticatedUser() {
        authenticator.result = new AuthenticatedUser("loginuser@example.com");

        AccessToken token = authService.login(new Credentials("loginuser@example.com", "secret"));

        assertThat(token.value()).isEqualTo("token:loginuser@example.com");
    }

    @Test
    void loginPropagatesInvalidCredentials() {
        authenticator.throwInvalid = true;

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> authService.login(new Credentials("x@example.com", "wrong")));
    }

    private static final class FakeUserRepository implements UserRepositoryPort {
        private final Map<String, UserAccount> accounts = new HashMap<>();
        private boolean failCreateWithDuplicate;

        @Override
        public boolean existsByEmail(String email) {
            return accounts.containsKey(email);
        }

        @Override
        public AuthenticatedUser create(String email, String passwordHash) {
            if (failCreateWithDuplicate) {
                throw new EmailAlreadyRegisteredException();
            }
            accounts.put(email, new UserAccount(email, passwordHash));
            return new AuthenticatedUser(email);
        }

        @Override
        public Optional<UserAccount> findByEmail(String email) {
            return Optional.ofNullable(accounts.get(email));
        }
    }

    private static final class RecordingAuthenticator implements CredentialAuthenticatorPort {
        private AuthenticatedUser result;
        private boolean throwInvalid;

        @Override
        public AuthenticatedUser authenticate(Credentials credentials) {
            if (throwInvalid) {
                throw new InvalidCredentialsException();
            }
            return result;
        }
    }
}

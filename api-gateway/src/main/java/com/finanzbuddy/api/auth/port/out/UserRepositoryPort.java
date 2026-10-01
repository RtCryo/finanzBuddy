package com.finanzbuddy.api.auth.port.out;

import com.finanzbuddy.api.auth.domain.model.AuthenticatedUser;
import com.finanzbuddy.api.auth.domain.model.UserAccount;
import java.util.Optional;

public interface UserRepositoryPort {

    boolean existsByEmail(String email);

    /**
     * Persists a new user. Implementations translate a unique-constraint violation into {@link
     * com.finanzbuddy.api.auth.domain.exception.EmailAlreadyRegisteredException}.
     */
    AuthenticatedUser create(String email, String passwordHash);

    Optional<UserAccount> findByEmail(String email);
}

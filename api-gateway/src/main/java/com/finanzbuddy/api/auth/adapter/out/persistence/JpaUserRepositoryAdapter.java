package com.finanzbuddy.api.auth.adapter.out.persistence;

import com.finanzbuddy.api.auth.domain.exception.EmailAlreadyRegisteredException;
import com.finanzbuddy.api.auth.domain.model.AuthenticatedUser;
import com.finanzbuddy.api.auth.domain.model.UserAccount;
import com.finanzbuddy.api.auth.port.out.UserRepositoryPort;
import com.finanzbuddy.api.entity.User;
import com.finanzbuddy.api.repository.UserRepository;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class JpaUserRepositoryAdapter implements UserRepositoryPort {

    private final UserRepository userRepository;

    public JpaUserRepositoryAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public AuthenticatedUser create(String email, String passwordHash) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // The unique index is the authoritative guard against a concurrent duplicate INSERT
            // that slipped past the existsByEmail pre-check.
            throw new EmailAlreadyRegisteredException();
        }
        return new AuthenticatedUser(user.getEmail());
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return userRepository
                .findByEmail(email)
                .map(user -> new UserAccount(user.getEmail(), user.getPasswordHash()));
    }
}

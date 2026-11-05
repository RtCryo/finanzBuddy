package com.finanzbuddy.api.security;

import com.finanzbuddy.api.entity.User;
import com.finanzbuddy.api.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class HomeBuddyAccountResolver {

    private final UserRepository users;
    private final TransactionTemplate transactions;

    public HomeBuddyAccountResolver(UserRepository users, PlatformTransactionManager manager) {
        this.users = users;
        this.transactions = new TransactionTemplate(manager);
    }

    public User resolve(Jwt jwt) {
        UUID accountId;
        try {
            accountId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadCredentialsException("Invalid account subject", e);
        }
        String claimEmail = jwt.getClaimAsString("email");
        if (claimEmail == null || claimEmail.isBlank()) {
            throw new BadCredentialsException("Missing account email");
        }
        String email = claimEmail.trim().toLowerCase(Locale.ROOT);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return transactions.execute(status -> resolveOnce(accountId, email));
            } catch (DataIntegrityViolationException e) {
                if (attempt == 2) {
                    throw new BadCredentialsException("Account link conflict", e);
                }
            }
        }
        throw new IllegalStateException("Unreachable account resolution state");
    }

    private User resolveOnce(UUID accountId, String email) {
        User user = users.findByAccountId(accountId).orElse(null);
        if (user != null) {
            if (!user.getEmail().equals(email)) {
                user.setEmail(email);
                return users.saveAndFlush(user);
            }
            return user;
        }
        user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user != null) {
            if (user.getAccountId() != null) {
                throw new BadCredentialsException("Email belongs to another account");
            }
            user.setAccountId(accountId);
        } else {
            user = new User();
            user.setAccountId(accountId);
            user.setEmail(email);
        }
        return users.saveAndFlush(user);
    }
}

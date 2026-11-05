package com.finanzbuddy.api.security;

import com.finanzbuddy.api.entity.User;
import com.finanzbuddy.api.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Maps a homeBuddy token to its local {@code users} row by account id only.
 *
 * <p>Legacy rows are linked to their imported accounts by the operator link step of the migration
 * runbook, never by a matching email at request time. A new account whose email still belongs to an
 * unlinked legacy row is rejected so that it cannot take over that row's data.
 */
@Service
public class HomeBuddyAccountResolver {

    private static final Logger log = LoggerFactory.getLogger(HomeBuddyAccountResolver.class);

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
                // Only a concurrent first request for the same account can collide (account_id).
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
        if (users.existsByEmailIgnoreCaseAndAccountIdIsNull(email)) {
            log.warn(
                    "Rejected account {}: its email belongs to an unlinked legacy user. Run the"
                            + " link step of docs/HOMEBUDDY_IDENTITY_MIGRATION.md.",
                    accountId);
            throw new BadCredentialsException("Email belongs to an unlinked legacy user");
        }
        user = new User();
        user.setAccountId(accountId);
        user.setEmail(email);
        return users.saveAndFlush(user);
    }
}

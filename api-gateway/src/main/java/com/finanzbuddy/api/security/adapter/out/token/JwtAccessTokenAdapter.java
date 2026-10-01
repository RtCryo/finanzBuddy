package com.finanzbuddy.api.security.adapter.out.token;

import com.finanzbuddy.api.security.domain.model.TokenValidationResult;
import com.finanzbuddy.api.security.port.out.AccessTokenPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The only place {@code io.jsonwebtoken} is used; translates its exceptions to domain results. */
@Component
public class JwtAccessTokenAdapter implements AccessTokenPort {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtAccessTokenAdapter(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.secret (env JWT_SECRET) must be set to a Base64-encoded signing key;"
                            + " refusing to start without one");
        }
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
    }

    @Override
    public String issue(String subject) {
        // No custom claims: authorities are always reloaded from the database when a request is
        // authenticated, so a roles claim would be dead weight (and must not be trusted).
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(Map.of())
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(secretKey)
                .compact();
    }

    @Override
    public TokenValidationResult validate(String token) {
        try {
            // parseSignedClaims verifies the signature and rejects expired tokens, so a token that
            // parses here is unexpired.
            Claims claims =
                    Jwts.parser()
                            .verifyWith(secretKey)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
            return TokenValidationResult.valid(claims.getSubject());
        } catch (ExpiredJwtException e) {
            return TokenValidationResult.expired(e.getClaims().getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return TokenValidationResult.invalid();
        }
    }
}

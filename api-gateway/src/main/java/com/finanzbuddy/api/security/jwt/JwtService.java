package com.finanzbuddy.api.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
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

    public String generateToken(UserDetails userDetails) {
        // No custom claims: authorities are always reloaded from the database in the auth filter,
        // so a roles claim would be dead weight (and must not be trusted).
        return createToken(Map.of(), userDetails.getUsername());
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Parses and signature-verifies the token once, returning its claims. Throws {@link
     * io.jsonwebtoken.ExpiredJwtException} for expired tokens and {@link JwtException} /
     * {@link IllegalArgumentException} for malformed ones, letting callers avoid a second parse.
     */
    public Claims parseClaims(String token) {
        return extractAllClaims(token);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            // parseSignedClaims already rejects expired tokens with ExpiredJwtException, so a token
            // that parses here is unexpired; only the subject still needs checking.
            Claims claims = extractAllClaims(token);
            return claims.getSubject().equals(userDetails.getUsername());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }

    private String createToken(Map<String, Object> claims, String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(secretKey)
                .compact();
    }
}

package com.hdfc.accountservice.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Optional;

/**
 * Validates JWT access tokens issued by User Service.
 *
 * <p>Account Service never issues tokens — it only validates them.
 * The secret must match the JWT_SECRET environment variable used
 * by User Service exactly. If they differ, every authenticated
 * request will be rejected with 401.
 *
 * <p>DESIGN PATTERN — Single Responsibility:
 * This class does exactly one thing — validate and parse JWTs.
 * It has no knowledge of Spring Security, HTTP requests, or
 * account domain logic.
 */
@Component
@Slf4j
public class JwtTokenValidator {

    private final SecretKey signingKey;

    /**
     * Constructs the validator by decoding the Base64-encoded secret
     * from the JWT_SECRET environment variable.
     *
     * <p>The key is built once at startup — not on every request.
     * This avoids repeated Base64 decoding on the hot path.
     *
     * @param secret the Base64-encoded HMAC-SHA256 signing secret
     */
    public JwtTokenValidator(@Value("${jwt.secret}") String secret) {
        // Decode the Base64 secret into raw bytes and wrap in a
        // SecretKey suitable for HMAC-SHA256 signing verification.
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Validates a JWT token and extracts its claims if valid.
     *
     * <p>Returns an empty Optional for any invalid token rather than
     * throwing — the JWT filter uses the Optional to decide whether
     * to set the SecurityContext or reject the request.
     *
     * @param token the raw JWT string (without "Bearer " prefix)
     * @return an Optional containing the claims, or empty if invalid
     */
    public Optional<Claims> validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException ex) {
            // JwtException covers: expired, malformed, wrong signature,
            // unsupported algorithm. We log at DEBUG — these are expected
            // in normal operation (expired tokens) and WARN-level logging
            // would flood the logs.
            log.debug("JWT validation failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extracts the subject (user email) from a validated claims object.
     *
     * @param claims the validated JWT claims
     * @return the user's email address
     */
    public String extractEmail(Claims claims) {
        return claims.getSubject();
    }

    /**
     * Extracts the role claim from a validated claims object.
     *
     * <p>The claim name "role" and format "ROLE_CUSTOMER" must match
     * User Service's token generation exactly. Any mismatch will cause
     * Spring Security role checks to fail silently.
     *
     * @param claims the validated JWT claims
     * @return the role string e.g. "ROLE_CUSTOMER", "ROLE_ADMIN"
     */
    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }

    /**
     * Extracts the userId claim from a validated claims object.
     *
     * <p>The claim key "userId" and value format (Long as String) must
     * match User Service's JWT generation exactly — set in JwtServiceImpl
     * after the fix/jwt-userid-claim branch was merged.
     *
     * @param claims the validated JWT claims
     * @return the user's ID
     */
    public Long extractUserId(Claims claims) {
        String userIdStr = claims.get("userId", String.class);
        return Long.parseLong(userIdStr);
    }
}
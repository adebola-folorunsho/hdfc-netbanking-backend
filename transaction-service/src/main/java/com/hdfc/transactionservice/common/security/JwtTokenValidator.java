package com.hdfc.transactionservice.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Validates JWTs issued by User Service and extracts claims.
 *
 * <p>Transaction Service never issues tokens — it only validates them.
 * The same JWT_SECRET shared across all services is used for validation.
 *
 * <p>Three claims are extracted per request:
 * <ul>
 *   <li>subject — user email (set as JWT subject by User Service)</li>
 *   <li>role — "ROLE_CUSTOMER", "ROLE_TELLER", or "ROLE_ADMIN"</li>
 *   <li>userId — the database ID of the authenticated user (Long as String)</li>
 * </ul>
 */
@Component
@Slf4j
public class JwtTokenValidator {

    private final SecretKey signingKey;

    public JwtTokenValidator(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validates the token signature and expiry, then returns all claims.
     *
     * @param token the raw JWT string (without "Bearer " prefix)
     * @return the parsed claims
     * @throws JwtException if the token is invalid, expired, or tampered
     */
    public Claims validateAndGetClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the subject (email) from a validated claims object.
     *
     * @param claims the parsed JWT claims
     * @return the user email
     */
    public String extractEmail(Claims claims) {
        return claims.getSubject();
    }

    /**
     * Extracts the role claim from validated claims.
     *
     * @param claims the parsed JWT claims
     * @return the role string, e.g. "ROLE_CUSTOMER"
     */
    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }

    /**
     * Extracts the userId claim from validated claims.
     *
     * <p>userId is stored as a String in the JWT (set by User Service
     * fix/jwt-userid-claim — merged to main). It is parsed back to
     * Long here for use throughout Transaction Service.
     *
     * @param claims the parsed JWT claims
     * @return the userId as Long
     */
    public Long extractUserId(Claims claims) {
        String userIdStr = claims.get("userId", String.class);
        return Long.parseLong(userIdStr);
    }

    /**
     * Checks whether the token has expired.
     *
     * @param claims the parsed JWT claims
     * @return true if the token is expired
     */
    public boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}
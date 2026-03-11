package com.hdfc.transactionservice.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Generates and caches a service-level JWT for Transaction Service
 * to authenticate with Account Service.
 *
 * <p>This JWT is used exclusively for internal service-to-service
 * calls — never returned to clients or exposed via any endpoint.
 *
 * <p>Token properties (per master planning decision):
 * <ul>
 *   <li>subject: "transaction-service"</li>
 *   <li>role claim: "ROLE_ADMIN"</li>
 *   <li>expiry: 24 hours from generation</li>
 *   <li>signed with the shared JWT_SECRET</li>
 * </ul>
 *
 * <p>The token is generated once at startup via @PostConstruct
 * and cached for the lifetime of the service instance. On the
 * next restart a fresh token is generated automatically.
 *
 * <p>Account Service validates this token identically to user JWTs —
 * same secret, same parser. The ROLE_ADMIN claim passes Account
 * Service's TELLER/ADMIN role restriction on debit/credit endpoints.
 */
@Component
@Slf4j
public class ServiceJwtProvider {

    private static final long EXPIRY_MS = 24 * 60 * 60 * 1000L; // 24 hours

    private final SecretKey signingKey;
    private String cachedToken;
    private long tokenExpiresAt;

    public ServiceJwtProvider(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates the service JWT at startup and caches it.
     * Called automatically by Spring after bean initialisation.
     */
    @PostConstruct
    public void init() {
        generateAndCacheToken();
        log.info("Service JWT generated for transaction-service " +
                "(expires in 24 hours)");
    }

    /**
     * Returns the cached service JWT, regenerating if expired.
     *
     * <p>The 5-minute buffer before expiry ensures the token is
     * never used in the last few minutes of its validity window —
     * guarding against clock skew between services.
     *
     * @return the service-level JWT string (without "Bearer " prefix)
     */
    public String getServiceToken() {
        // Regenerate if within 5 minutes of expiry.
        if (System.currentTimeMillis() > tokenExpiresAt - (5 * 60 * 1000L)) {
            log.info("Service JWT expiring soon — regenerating");
            generateAndCacheToken();
        }
        return cachedToken;
    }

    /**
     * Generates a new service JWT and updates the cache.
     */
    private void generateAndCacheToken() {
        long now = System.currentTimeMillis();
        tokenExpiresAt = now + EXPIRY_MS;

        cachedToken = Jwts.builder()
                .subject("transaction-service")
                .claim("role", "ROLE_ADMIN")
                .claim("userId", "0")  // Service identity — not a real user ID.
                // Account Service extracts userId from
                // JWT for ownership checks. userId=0
                // is never a valid database ID so it
                // will never accidentally match a real
                // user's account ownership check.
                .issuedAt(new Date(now))
                .expiration(new Date(tokenExpiresAt))
                .signWith(signingKey)
                .compact();
    }
}
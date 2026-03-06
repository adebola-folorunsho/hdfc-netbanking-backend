package com.hdfc.userservice.common.security.jwt;

import com.hdfc.userservice.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;

/**
 * Concrete implementation of {@link JwtService} using the JJWT library.
 *
 * <p>Handles JWT token generation, signing, and validation using HMAC-SHA256.
 * The signing secret is injected from the {@code JWT_SECRET} environment
 * variable via {@code application.yml} — never hardcoded.
 *
 * <p>Token structure:
 * <ul>
 *   <li>Header: algorithm (HS256) and token type (JWT)</li>
 *   <li>Payload: subject (email), role claim, issued-at, expiration</li>
 *   <li>Signature: HMAC-SHA256 signed with the shared secret</li>
 * </ul>
 *
 * <p>The {@code role} claim is included in every token so both the API
 * Gateway and Admin Gateway can enforce access control independently
 * without making a call back to this service — stateless validation.
 */
@Slf4j
@Service
public class JwtServiceImpl implements JwtService {

    /**
     * The secret key used to sign and verify JWT tokens.
     * Read from the JWT_SECRET environment variable via application.yml.
     * Must be a minimum of 256 bits (32 bytes) for HMAC-SHA256.
     */
    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    /**
     * Access token validity period in milliseconds.
     * Set to 900000 (15 minutes) per Section 5.1 of the project spec.
     */
    @Value("${application.security.jwt.expiration}")
    private long accessTokenExpiration;

    /**
     * Refresh token validity period in milliseconds.
     * Set to 604800000 (7 days) per Section 5.1 of the project spec.
     */
    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    /**
     * {@inheritDoc}
     *
     * <p>Includes the user's role as a custom claim named {@code role}.
     * The role is prefixed with {@code ROLE_} to match Spring Security's
     * authority naming convention — readable by both gateways directly.
     */
    @Override
    public String generateAccessToken(User user) {
        Map<String, Object> extraClaims = new HashMap<>();

        // Include the role claim — both gateways read this for access control.
        // We take the first role since a user typically has one primary role.
        // The ROLE_ prefix is added here so gateways can use it directly.
        user.getRoles().stream()
                .findFirst()
                .ifPresent(role ->
                        extraClaims.put("role", "ROLE_" + role.name()));

        return buildToken(extraClaims, user.getEmail(), accessTokenExpiration);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Refresh tokens contain only the subject (email) — no role claim.
     * They are used solely for issuing new access tokens, not for
     * authorization decisions.
     */
    @Override
    public String generateRefreshToken(User user) {
        return buildToken(new HashMap<>(), user.getEmail(), refreshTokenExpiration);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        boolean isValid = email.equals(userDetails.getUsername())
                && !isTokenExpired(token);
        if (!isValid) {
            log.warn("Token validation failed for user: {}", email);
        }
        return isValid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Builds and signs a JWT token with the given claims, subject, and expiry.
     *
     * <p>Uses HMAC-SHA256 (HS256) — a symmetric algorithm where the same
     * secret is used for both signing and verification. This is appropriate
     * here because both signing (User Service) and verification (Gateways)
     * are internal services under our control. Asymmetric algorithms (RS256)
     * would be needed if external parties needed to verify tokens.
     *
     * @param extraClaims additional claims to include in the token payload
     * @param subject     the token subject — the user's email address
     * @param expiration  token validity period in milliseconds
     * @return a signed JWT token string
     */
    private String buildToken(
            Map<String, Object> extraClaims,
            String subject,
            long expiration) {

        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extracts a specific claim from a JWT token using the provided resolver function.
     *
     * <p>Design pattern: this method uses a Function as a strategy — callers
     * pass in how to extract their specific claim. This avoids duplicating
     * the token parsing logic for every claim type. Every public extract
     * method delegates here.
     *
     * @param token          the JWT token string to parse
     * @param claimsResolver a function that extracts the desired value from Claims
     * @param <T>            the type of the claim value being extracted
     * @return the extracted claim value
     */
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parses and returns all claims from a JWT token.
     *
     * <p>If the token is malformed, expired, or has an invalid signature,
     * JJWT throws a {@link io.jsonwebtoken.JwtException} which propagates
     * up and is caught by the JWT filter — resulting in a 401 response.
     *
     * @param token the JWT token string to parse
     * @return the full {@link Claims} object from the token payload
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the expiration date from a JWT token.
     *
     * @param token the JWT token string to parse
     * @return the expiration {@link Date}
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Decodes the Base64-encoded secret key and returns a {@link Key} object
     * suitable for HMAC-SHA256 signing.
     *
     * <p>The secret is stored as a hex string in the environment variable
     * and decoded here at runtime. The {@link Keys#hmacShaKeyFor} method
     * validates that the key is at least 256 bits — throwing an exception
     * at startup if the secret is too short, rather than silently using
     * a weak key.
     *
     * @return the signing {@link Key}
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
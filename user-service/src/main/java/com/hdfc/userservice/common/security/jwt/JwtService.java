package com.hdfc.userservice.common.security.jwt;

import com.hdfc.userservice.domain.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Contract for JWT token operations in the User Service.
 *
 * <p>Defines the full lifecycle of a JWT token — generation, validation,
 * and claim extraction. The auth feature and JWT filter depend on this
 * interface, never on the concrete implementation. This satisfies DIP
 * (Dependency Inversion Principle) and OCP (Open/Closed Principle) —
 * the token signing algorithm or structure can be changed by providing
 * a new implementation without touching any caller.
 *
 * <p>Follows ISP (Interface Segregation Principle) — this interface
 * covers only JWT concerns. Redis token storage is handled separately
 * by the auth service layer.
 */
public interface JwtService {

    /**
     * Generates a signed JWT access token for the given user.
     *
     * <p>The token contains the user's email as the subject and their
     * role as a custom claim. Both the API Gateway (port 8080) and the
     * Admin Gateway (port 8090) read the role claim to enforce access
     * control independently without calling this service.
     *
     * @param user the authenticated user for whom to generate the token
     * @return a signed JWT access token string
     */
    String generateAccessToken(User user);

    /**
     * Generates a signed JWT refresh token for the given user.
     *
     * <p>Refresh tokens have a longer expiry than access tokens (7 days
     * vs 15 minutes). They are stored in Redis and used to issue new
     * access tokens without requiring re-authentication.
     *
     * @param user the authenticated user for whom to generate the token
     * @return a signed JWT refresh token string
     */
    String generateRefreshToken(User user);

    /**
     * Extracts the subject (email address) from a JWT token.
     *
     * @param token the JWT token string to parse
     * @return the email address stored as the token subject
     */
    String extractEmail(String token);

    /**
     * Extracts the role claim from a JWT token.
     *
     * <p>The role is stored as a custom claim named {@code role} in the
     * token payload. Both gateways read this claim for access control.
     *
     * @param token the JWT token string to parse
     * @return the role string (e.g. "ROLE_ADMIN", "ROLE_CUSTOMER")
     */
    String extractRole(String token);

    /**
     * Validates a JWT token against the given user details.
     *
     * <p>Checks that:
     * <ul>
     *   <li>The token subject matches the user's email</li>
     *   <li>The token has not expired</li>
     *   <li>The token signature is valid</li>
     * </ul>
     *
     * @param token       the JWT token string to validate
     * @param userDetails the user details to validate the token against
     * @return true if the token is valid for the given user, false otherwise
     */
    boolean isTokenValid(String token, UserDetails userDetails);

    /**
     * Checks whether a JWT token has expired.
     *
     * @param token the JWT token string to check
     * @return true if the token expiry time is in the past, false otherwise
     */
    boolean isTokenExpired(String token);
}
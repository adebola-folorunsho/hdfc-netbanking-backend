package com.hdfc.userservice.auth;

import com.hdfc.userservice.auth.dto.AuthRequest;
import com.hdfc.userservice.auth.dto.AuthResponse;
import com.hdfc.userservice.auth.dto.RefreshTokenRequest;

/**
 * Contract for authentication operations in the User Service.
 *
 * <p>Defines login, token refresh, and logout. The controller and any
 * future caller depend on this interface — never on the concrete
 * implementation. Satisfies DIP and OCP.
 *
 * <p>Follows ISP — this interface covers only authentication concerns.
 * Registration, role management, and 2FA are in separate interfaces.
 */
public interface IAuthService {

    /**
     * Authenticates a user with their email and password.
     *
     * <p>On success:
     * <ol>
     *   <li>Verifies the password against the BCrypt hash</li>
     *   <li>Generates a JWT access token (15 min expiry)</li>
     *   <li>Generates a JWT refresh token (7 day expiry)</li>
     *   <li>Stores the refresh token in Redis under
     *       {@code user:refresh:{userId}} with 7 day TTL</li>
     *   <li>Returns both tokens to the client</li>
     * </ol>
     *
     * @param request the login credentials
     * @return an {@link AuthResponse} containing access and refresh tokens
     * @throws org.springframework.security.authentication.BadCredentialsException
     *         if the email or password is incorrect
     * @throws org.springframework.security.authentication.DisabledException
     *         if the user account is suspended
     */
    AuthResponse login(AuthRequest request);

    /**
     * Refreshes an expired access token using a valid refresh token.
     *
     * <p>Implements refresh token rotation — single-use tokens only:
     * <ol>
     *   <li>Validates the submitted refresh token signature and expiry</li>
     *   <li>Looks up the token in Redis — rejects if not found
     *       (already used or expired)</li>
     *   <li>Deletes the old refresh token from Redis immediately</li>
     *   <li>Generates a new access token and refresh token</li>
     *   <li>Stores the new refresh token in Redis</li>
     *   <li>Returns both new tokens to the client</li>
     * </ol>
     *
     * <p>If the same refresh token is submitted twice, the second
     * request is rejected — preventing replay attacks.
     *
     * @param request the refresh token request
     * @return a new {@link AuthResponse} with fresh tokens
     * @throws com.hdfc.userservice.common.exception.InvalidTokenException
     *         if the refresh token is invalid, expired, or already used
     */
    AuthResponse refresh(RefreshTokenRequest request);

    /**
     * Logs out a user by invalidating their refresh token in Redis.
     *
     * <p>Deletes the refresh token stored under {@code user:refresh:{userId}}
     * from Redis. The access token remains technically valid until its
     * natural 15-minute expiry — this is acceptable given the short TTL.
     *
     * <p>After logout, any attempt to use the old refresh token
     * is rejected because it no longer exists in Redis.
     *
     * @param email the email address of the user to log out
     */
    void logout(String email);
}
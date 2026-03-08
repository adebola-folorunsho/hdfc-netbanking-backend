package com.hdfc.userservice.common.exception;

/**
 * Thrown when a JWT access token or refresh token is invalid,
 * malformed, expired, or has been revoked.
 *
 * <p>Maps to HTTP 401 Unauthorized via the global exception handler.
 * Thrown by the JWT service during token validation and by the
 * refresh token rotation logic when a token cannot be found in Redis
 * or has already been used (replay attack prevention).
 */
public class InvalidTokenException extends UserServiceException {

    /**
     * @param reason a specific description of why the token was rejected
     */
    public InvalidTokenException(String reason) {
        super("Invalid token: " + reason);
    }
}
package com.hdfc.userservice.common.exception;

/**
 * Thrown when a 2FA operation fails for reasons other than an invalid OTP.
 *
 * <p>Maps to HTTP 400 Bad Request via the global exception handler.
 * Examples: attempting to enable 2FA when it is already enabled,
 * attempting to disable 2FA when it was never enabled, or attempting
 * to verify a TOTP code before 2FA setup is complete.
 */
public class TwoFactorAuthException extends UserServiceException {

    /**
     * @param reason a specific description of why the 2FA operation failed
     */
    public TwoFactorAuthException(String reason) {
        super("2FA operation failed: " + reason);
    }
}
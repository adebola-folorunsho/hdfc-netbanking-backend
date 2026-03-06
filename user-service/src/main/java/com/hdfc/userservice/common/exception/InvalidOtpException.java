package com.hdfc.userservice.common.exception;

/**
 * Thrown when a TOTP code submitted during 2FA verification is
 * invalid, expired, or does not match the user's secret.
 *
 * <p>Maps to HTTP 401 Unauthorized via the global exception handler.
 * OTP codes have a 30-second TTL in Redis — codes submitted after
 * expiry also trigger this exception.
 */
public class InvalidOtpException extends UserServiceException {

    public InvalidOtpException() {
        super("Invalid or expired OTP code. Please try again.");
    }
}
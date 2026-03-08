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

    /**
     * Constructs with the default message — used when a submitted
     * TOTP code is simply wrong or expired during normal validation.
     */
    public InvalidOtpException() {
        super("Invalid or expired OTP code. Please try again.");
    }

    /**
     * Constructs with a specific message — used when a more descriptive
     * reason is available, such as an expired setup session.
     *
     * @param reason a specific description of why the OTP was rejected
     */
    public InvalidOtpException(String reason) {
        super(reason);
    }
}
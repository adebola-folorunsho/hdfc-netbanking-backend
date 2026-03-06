package com.hdfc.userservice.common.exception;

/**
 * Thrown when KYC validation fails during user registration.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity via the global exception handler.
 * Triggered when submitted KYC details (name, address, government ID)
 * fail validation rules or conflict with existing records.
 */
public class KycVerificationException extends UserServiceException {

    /**
     * @param reason a specific description of why KYC validation failed
     */
    public KycVerificationException(String reason) {
        super("KYC verification failed: " + reason);
    }
}
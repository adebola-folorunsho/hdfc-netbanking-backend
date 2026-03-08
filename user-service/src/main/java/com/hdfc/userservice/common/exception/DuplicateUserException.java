package com.hdfc.userservice.common.exception;

/**
 * Thrown when a registration attempt uses an email, phone number,
 * or government ID that already exists in the system.
 *
 * <p>Maps to HTTP 409 Conflict via the global exception handler.
 * Prevents duplicate account creation — a core KYC requirement.
 */
public class DuplicateUserException extends UserServiceException {

    /**
     * @param field the field that caused the conflict (e.g. "email", "phoneNumber")
     * @param value the duplicate value that was rejected
     */
    public DuplicateUserException(String field, String value) {
        super("Duplicate value for field '" + field + "': " + value);
    }
}
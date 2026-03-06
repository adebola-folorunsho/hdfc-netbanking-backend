package com.hdfc.userservice.common.exception;

/**
 * Thrown when a user lookup fails to find a matching record.
 *
 * <p>Maps to HTTP 404 Not Found via the global exception handler.
 * Thrown by service methods when querying by email, phone number,
 * government ID, or user ID yields no result.
 */
public class UserNotFoundException extends UserServiceException {

    /**
     * @param identifier the value that was searched for (email, id, etc.)
     *                   — included in the message for debuggability
     */
    public UserNotFoundException(String identifier) {
        super("User not found: " + identifier);
    }
}
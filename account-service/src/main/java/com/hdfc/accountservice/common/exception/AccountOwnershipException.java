package com.hdfc.accountservice.common.exception;

/**
 * Thrown when a user attempts to access or operate on an account
 * that does not belong to them.
 *
 * <p>This is a security boundary check performed at the service layer
 * before any account operation. Even if the JWT is valid, a user
 * must only be able to operate on their own accounts unless they
 * hold TELLER or ADMIN role.
 *
 * <p>Maps to HTTP 403 Forbidden in GlobalExceptionHandler.
 */
public class AccountOwnershipException extends AccountServiceException {

    public AccountOwnershipException(String message) {
        super(message);
    }
}
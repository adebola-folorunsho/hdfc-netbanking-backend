package com.hdfc.accountservice.common.exception;

/**
 * Thrown when an operation is attempted on an account that is not
 * in ACTIVE status.
 *
 * <p>Covers all non-ACTIVE states: INACTIVE, FROZEN, CLOSED.
 * The exception message must specify which status the account
 * actually holds so the caller can give a meaningful error to the user.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity in GlobalExceptionHandler.
 */
public class AccountNotActiveException extends AccountServiceException {

    public AccountNotActiveException(String message) {
        super(message);
    }
}
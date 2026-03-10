package com.hdfc.accountservice.common.exception;

/**
 * Thrown when an account lookup fails — by ID, account number,
 * or any other identifier.
 *
 * <p>Maps to HTTP 404 Not Found in GlobalExceptionHandler.
 */
public class AccountNotFoundException extends AccountServiceException {

    public AccountNotFoundException(String message) {
        super(message);
    }
}
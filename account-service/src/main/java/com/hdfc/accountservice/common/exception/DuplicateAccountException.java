package com.hdfc.accountservice.common.exception;

/**
 * Thrown when a user attempts to create an account of a type they
 * already hold.
 *
 * <p>Business rule: one account per type per user.
 * e.g. a user cannot open two SAVINGS accounts.
 *
 * <p>Maps to HTTP 409 Conflict in GlobalExceptionHandler.
 */
public class DuplicateAccountException extends AccountServiceException {

    public DuplicateAccountException(String message) {
        super(message);
    }
}
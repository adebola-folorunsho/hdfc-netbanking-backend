package com.hdfc.accountservice.common.exception;

/**
 * Thrown when a debit or transfer is attempted but the account
 * does not have sufficient funds to cover the amount plus any
 * applicable minimum balance requirement.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity in GlobalExceptionHandler.
 * 422 is semantically correct here — the request is well-formed
 * but cannot be processed due to a business rule violation.
 */
public class InsufficientBalanceException extends AccountServiceException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
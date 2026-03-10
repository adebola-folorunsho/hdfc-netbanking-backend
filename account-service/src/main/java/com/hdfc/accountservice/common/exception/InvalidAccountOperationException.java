package com.hdfc.accountservice.common.exception;

/**
 * Thrown when an operation is logically invalid for the account type
 * or its current state — regardless of balance or status.
 *
 * <p>Examples:
 * <ul>
 *   <li>Attempting to withdraw from a FIXED_DEPOSIT before maturity</li>
 *   <li>Attempting to close an account with a non-zero balance</li>
 *   <li>Attempting to change the account type after creation</li>
 * </ul>
 *
 * <p>Maps to HTTP 400 Bad Request in GlobalExceptionHandler.
 */
public class InvalidAccountOperationException extends AccountServiceException {

    public InvalidAccountOperationException(String message) {
        super(message);
    }
}
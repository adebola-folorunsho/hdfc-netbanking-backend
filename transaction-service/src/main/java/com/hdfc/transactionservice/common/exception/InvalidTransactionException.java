package com.hdfc.transactionservice.common.exception;

/**
 * Thrown when a transaction request is logically invalid.
 *
 * <p>Examples:
 * <ul>
 *   <li>Transferring to the same account as the source</li>
 *   <li>Attempting to reverse a FAILED or already REVERSED transaction</li>
 *   <li>Submitting a zero or negative amount</li>
 *   <li>Requesting a currency not supported by Currency Service</li>
 * </ul>
 *
 * <p>Maps to HTTP 400 Bad Request in GlobalExceptionHandler.
 */
public class InvalidTransactionException extends TransactionServiceException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
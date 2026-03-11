package com.hdfc.transactionservice.common.exception;

/**
 * Thrown when a user attempts to access a transaction that
 * does not belong to them.
 *
 * <p>A CUSTOMER can only view their own transaction history.
 * If they request a transaction ID that belongs to another user,
 * this exception is thrown — we do not reveal whether the
 * transaction exists at all.
 *
 * <p>Maps to HTTP 403 Forbidden in GlobalExceptionHandler.
 */
public class TransactionOwnershipException extends TransactionServiceException {

    public TransactionOwnershipException(String message) {
        super(message);
    }
}
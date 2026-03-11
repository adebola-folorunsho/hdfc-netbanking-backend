package com.hdfc.transactionservice.common.exception;

/**
 * Thrown when a transaction with the same reference already exists.
 *
 * <p>This is the idempotency guard. If a client retries a request
 * with the same transactionReference, we detect the duplicate and
 * throw this exception rather than processing the transfer twice.
 * The GlobalExceptionHandler returns the appropriate response so
 * the client knows the original request was already processed.
 *
 * <p>Maps to HTTP 409 Conflict in GlobalExceptionHandler.
 */
public class TransactionAlreadyExistsException extends TransactionServiceException {

    public TransactionAlreadyExistsException(String message) {
        super(message);
    }
}
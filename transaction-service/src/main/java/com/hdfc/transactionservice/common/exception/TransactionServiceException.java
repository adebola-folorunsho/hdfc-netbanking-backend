package com.hdfc.transactionservice.common.exception;

/**
 * Abstract base exception for all Transaction Service exceptions.
 *
 * <p>Every exception in Transaction Service extends this class.
 * GlobalExceptionHandler catches all subtypes through this base.
 * Extends RuntimeException — callers are not forced to declare
 * checked exceptions, keeping service method signatures clean.
 */
public abstract class TransactionServiceException extends RuntimeException {

    protected TransactionServiceException(String message) {
        super(message);
    }

    protected TransactionServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
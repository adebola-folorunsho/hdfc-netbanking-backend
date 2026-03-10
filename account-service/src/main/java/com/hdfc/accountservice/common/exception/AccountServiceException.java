package com.hdfc.accountservice.common.exception;

/**
 * Abstract base exception for all Account Service exceptions.
 *
 * <p>Every exception in Account Service extends this class.
 * This allows the GlobalExceptionHandler to catch all service-specific
 * exceptions in a single handler if needed, while still allowing
 * individual handlers for each specific exception type.
 *
 * <p>Extends RuntimeException — callers are not forced to declare
 * checked exceptions, keeping service method signatures clean.
 */
public abstract class AccountServiceException extends RuntimeException {

    protected AccountServiceException(String message) {
        super(message);
    }

    protected AccountServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
package com.hdfc.userservice.common.exception;

/**
 * Base exception for all User Service domain exceptions.
 *
 * <p>All custom exceptions in this service extend this class rather than
 * extending {@link RuntimeException} directly. This gives us a single
 * catch point if we ever need to handle any User Service exception generically,
 * while still allowing specific handlers for each subtype.
 *
 * <p>Extends {@link RuntimeException} because these are unchecked exceptions —
 * callers are not forced to declare or catch them. In a Spring service layer,
 * checked exceptions add noise without value since the global
 * {@code @ControllerAdvice} handler catches everything at the boundary.
 *
 * <p>Every subclass must provide a meaningful message and, where relevant,
 * context data about what caused the exception — never throw with a vague
 * or empty message.
 */
public abstract class UserServiceException extends RuntimeException {

    /**
     * Constructs a new exception with the given detail message.
     *
     * @param message a clear, human-readable description of what went wrong
     */
    protected UserServiceException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with a detail message and a root cause.
     *
     * <p>Use this constructor when wrapping a lower-level exception
     * (e.g. a database error) so the original cause is preserved in
     * the stack trace and not silently swallowed.
     *
     * @param message a clear, human-readable description of what went wrong
     * @param cause   the underlying exception that triggered this one
     */
    protected UserServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
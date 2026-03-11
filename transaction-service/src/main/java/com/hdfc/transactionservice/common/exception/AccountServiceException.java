package com.hdfc.transactionservice.common.exception;

/**
 * Thrown when a REST call to Account Service fails during
 * Saga orchestration.
 *
 * <p>Covers all failure scenarios when calling Account Service:
 * <ul>
 *   <li>Account Service is unavailable (connection refused)</li>
 *   <li>Account Service returns a 4xx or 5xx error response</li>
 *   <li>WebClient timeout on a debit or credit call</li>
 * </ul>
 *
 * <p>When this exception is thrown after a debit has already
 * succeeded, the Saga compensation logic in TransactionService
 * applies a compensating credit to restore the source balance.
 *
 * <p>Maps to HTTP 502 Bad Gateway in GlobalExceptionHandler —
 * the error originates from an upstream service, not from
 * Transaction Service itself.
 */
public class AccountServiceException extends TransactionServiceException {

    public AccountServiceException(String message) {
        super(message);
    }

    public AccountServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
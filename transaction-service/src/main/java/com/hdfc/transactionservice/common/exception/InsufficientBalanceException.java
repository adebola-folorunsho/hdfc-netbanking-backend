package com.hdfc.transactionservice.common.exception;

/**
 * Thrown when a transfer cannot proceed because the source account
 * does not have sufficient funds.
 *
 * <p>This exception is thrown by TransactionService when Account
 * Service returns an insufficient balance error during the Saga
 * debit step. It wraps the error from Account Service into a
 * Transaction Service domain exception.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity in GlobalExceptionHandler.
 */
public class InsufficientBalanceException extends TransactionServiceException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
package com.hdfc.transactionservice.common.exception;

/**
 * Thrown when a transaction lookup fails — by ID, reference,
 * or Paystack reference.
 *
 * <p>Maps to HTTP 404 Not Found in GlobalExceptionHandler.
 */
public class TransactionNotFoundException extends TransactionServiceException {

    public TransactionNotFoundException(String message) {
        super(message);
    }
}
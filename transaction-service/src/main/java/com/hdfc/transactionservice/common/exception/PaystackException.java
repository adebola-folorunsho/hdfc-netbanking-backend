package com.hdfc.transactionservice.common.exception;

/**
 * Thrown when a Paystack API call fails.
 *
 * <p>Covers all Paystack-specific failure scenarios:
 * <ul>
 *   <li>Paystack API returns an error response</li>
 *   <li>Invalid or expired Paystack reference</li>
 *   <li>Webhook signature verification failure</li>
 *   <li>Paystack API timeout</li>
 * </ul>
 *
 * <p>Maps to HTTP 502 Bad Gateway in GlobalExceptionHandler —
 * the error originates from the Paystack external service.
 */
public class PaystackException extends TransactionServiceException {

    public PaystackException(String message) {
        super(message);
    }

    public PaystackException(String message, Throwable cause) {
        super(message, cause);
    }
}
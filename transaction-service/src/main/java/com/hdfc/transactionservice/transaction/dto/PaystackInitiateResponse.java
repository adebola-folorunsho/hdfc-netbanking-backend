package com.hdfc.transactionservice.transaction.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO returned after successfully initiating a Paystack payment.
 *
 * <p>The authorizationUrl is the Paystack-hosted payment page URL.
 * The client redirects the user to this URL to complete payment.
 * Paystack then sends a webhook to confirm or reject the payment.
 *
 * <p>The accessCode is used by Paystack's inline JS library
 * (Paystack Popup) as an alternative to redirect flow.
 */
@Data
@Builder
public class PaystackInitiateResponse {

    /**
     * The internal transaction ID created by Transaction Service.
     */
    private Long transactionId;

    /**
     * Our internal transaction reference — for client tracking.
     */
    private String transactionReference;

    /**
     * Paystack-generated payment reference.
     * Stored on the Transaction record for webhook lookup.
     */
    private String paystackReference;

    /**
     * Paystack-hosted payment page URL.
     * Client redirects user here to complete payment.
     */
    private String authorizationUrl;

    /**
     * Paystack access code for inline payment popup.
     * Alternative to authorizationUrl for embedded payment flow.
     */
    private String accessCode;
}
package com.hdfc.transactionservice.transaction;

/**
 * Represents the type of a financial transaction.
 *
 * <p>Type determines which processing path the transaction takes:
 * <ul>
 *   <li>INTERNAL_TRANSFER — both accounts are in HDFC NetBanking.
 *       Account Service is called directly for debit and credit.</li>
 *   <li>PAYSTACK_PAYMENT — external payment via Paystack gateway.
 *       Paystack API is called to initiate and verify the payment.</li>
 *   <li>DEPOSIT — funds added to an account (e.g. cash deposit by teller).
 *       Only a credit operation on the destination account.</li>
 *   <li>WITHDRAWAL — funds removed from an account (e.g. ATM withdrawal).
 *       Only a debit operation on the source account.</li>
 * </ul>
 */
public enum TransactionType {

    /**
     * Transfer between two accounts within HDFC NetBanking.
     * Requires both a debit on the source and a credit on the destination.
     * Orchestrated by TransactionService as a Saga — if the credit fails
     * after the debit succeeds, a compensating credit is applied to source.
     */
    INTERNAL_TRANSFER,

    /**
     * External payment processed via Paystack payment gateway.
     * Primary currency: NGN. Sandbox mode uses Paystack test keys.
     * Transaction is only marked COMPLETED after Paystack webhook
     * confirms successful charge.
     */
    PAYSTACK_PAYMENT,

    /**
     * Deposit of funds into an account.
     * Performed by a TELLER or ADMIN — not available to CUSTOMER role.
     * Only a credit operation — no source account involved.
     */
    DEPOSIT,

    /**
     * Withdrawal of funds from an account.
     * Performed by a TELLER or ADMIN — not available to CUSTOMER role.
     * Only a debit operation — no destination account involved.
     */
    WITHDRAWAL
}
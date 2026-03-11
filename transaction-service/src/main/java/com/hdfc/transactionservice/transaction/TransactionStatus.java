package com.hdfc.transactionservice.transaction;

/**
 * Represents the lifecycle status of a financial transaction.
 *
 * <p>Status transitions:
 * <pre>
 *   PENDING → COMPLETED  (all Saga steps succeeded)
 *   PENDING → FAILED     (a Saga step failed, compensation applied)
 *   PENDING → REVERSED   (manually reversed by ADMIN after completion)
 *   COMPLETED → REVERSED (admin-initiated reversal)
 * </pre>
 *
 * <p>FAILED and REVERSED are terminal states — no further transitions.
 * PENDING is the initial state — every transaction starts here.
 */
public enum TransactionStatus {

    /**
     * Transaction has been initiated but not yet completed.
     * Account Service debit/credit calls are in progress.
     * Kafka event has not yet been published.
     */
    PENDING,

    /**
     * All Saga steps completed successfully.
     * Source account debited, destination account credited.
     * TRANSACTION_CREATED Kafka event published.
     * Terminal state for successful transactions.
     */
    COMPLETED,

    /**
     * A Saga step failed — compensation has been applied.
     * If the debit succeeded but the credit failed, a compensating
     * credit was applied to the source account to restore its balance.
     * The failure reason is recorded in the failureReason field.
     * Terminal state.
     */
    FAILED,

    /**
     * Transaction was reversed after completion by an ADMIN.
     * A compensating transaction pair (debit destination,
     * credit source) was applied. Terminal state.
     */
    REVERSED
}
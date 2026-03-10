package com.hdfc.accountservice.account;

/**
 * Represents the lifecycle status of a bank account.
 *
 * <p>Status transitions:
 * <pre>
 *   ACTIVE → INACTIVE  (manual suspension by teller/admin)
 *   ACTIVE → FROZEN    (automatic freeze by fraud detection — Phase 5)
 *   ACTIVE → CLOSED    (account closure, balance must be zero)
 *   INACTIVE → ACTIVE  (reactivation by admin)
 *   FROZEN → ACTIVE    (unfreeze by admin after review)
 *   CLOSED → (terminal — no transitions out of CLOSED)
 * </pre>
 */
public enum AccountStatus {

    /**
     * Account is fully operational.
     * Debits, credits, and transfers are all permitted.
     */
    ACTIVE,

    /**
     * Account is temporarily suspended.
     * No debits or transfers permitted.
     * Credits may still be received depending on business rules.
     */
    INACTIVE,

    /**
     * Account is permanently closed.
     * No operations permitted. Balance must be zero before closure.
     * Terminal state — cannot be reversed.
     */
    CLOSED,

    /**
     * Account is frozen by the fraud detection pipeline (Phase 5).
     * No debits permitted. Admin review required to unfreeze.
     * Credits may still be received to avoid blocking incoming transfers.
     */
    FROZEN
}
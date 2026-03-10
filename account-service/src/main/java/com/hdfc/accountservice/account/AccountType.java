package com.hdfc.accountservice.account;

/**
 * Represents the type of a bank account in the HDFC NetBanking system.
 *
 * <p>Each type has different business rules enforced at the service layer:
 * <ul>
 *   <li>SAVINGS     — minimum balance enforced, interest accrues monthly</li>
 *   <li>CURRENT     — no minimum balance, no interest, overdraft allowed</li>
 *   <li>FIXED_DEPOSIT — locked for a maturity period, higher interest rate</li>
 * </ul>
 */
public enum AccountType {

    /**
     * Standard savings account.
     * Minimum balance: enforced (configured per account at creation).
     * Interest: accrues monthly via Scheduler Service (Phase 6).
     */
    SAVINGS,

    /**
     * Current/checking account for businesses and high-frequency transactions.
     * Minimum balance: none.
     * Interest: none.
     * Overdraft: allowed up to configured limit.
     */
    CURRENT,

    /**
     * Fixed deposit account — funds locked until maturity date.
     * Minimum balance: deposit amount (immutable after creation).
     * Interest: higher rate, computed at maturity.
     * Early withdrawal: not permitted in this version.
     */
    FIXED_DEPOSIT
}
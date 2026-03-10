package com.hdfc.accountservice.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Request DTO for debit and credit operations on an account.
 *
 * <p>Called internally by Transaction Service via REST — not directly
 * by end users. Transaction Service is the orchestrator of all fund
 * movements. Account Service exposes debit and credit as atomic
 * operations that Transaction Service composes into transfers.
 *
 * <p>The reference field carries the Transaction Service transaction ID
 * for traceability — every balance change can be traced back to the
 * originating transaction record.
 */
@Getter
@Builder
public class DebitCreditRequest {

    /**
     * The amount to debit or credit.
     * Must be greater than zero — zero-value operations are rejected.
     * BigDecimal precision is enforced at the service layer with
     * HALF_EVEN rounding to 4 decimal places.
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private final BigDecimal amount;

    /**
     * ISO 4217 currency code of the amount being debited/credited.
     * Must match the account's currency code — cross-currency
     * debits/credits are not permitted at this layer.
     * Currency conversion is handled by Transaction Service
     * before calling this endpoint.
     */
    @NotBlank(message = "Currency code is required")
    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
    private final String currencyCode;

    /**
     * The Transaction Service transaction ID that originated this
     * debit or credit. Used for traceability and idempotency checks.
     * Every balance change must reference a transaction record.
     */
    @NotBlank(message = "Transaction reference is required")
    private final String transactionReference;
}
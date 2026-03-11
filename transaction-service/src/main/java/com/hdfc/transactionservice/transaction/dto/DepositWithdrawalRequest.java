package com.hdfc.transactionservice.transaction.dto;

import com.hdfc.transactionservice.transaction.TransactionType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request DTO for DEPOSIT and WITHDRAWAL transactions.
 *
 * <p>DEPOSIT and WITHDRAWAL are TELLER/ADMIN operations only.
 * CUSTOMER role cannot call these endpoints — enforced in
 * SecurityConfig and service layer.
 *
 * <p>DEPOSIT — credit only, no source account.
 * WITHDRAWAL — debit only, no destination account.
 * The transactionType field determines which operation is performed.
 */
@Data
public class DepositWithdrawalRequest {

    /**
     * The account ID to deposit into or withdraw from.
     */
    @NotNull(message = "Account ID is required")
    private Long accountId;

    /**
     * Must be DEPOSIT or WITHDRAWAL.
     * INTERNAL_TRANSFER and PAYSTACK_PAYMENT are rejected.
     */
    @NotNull(message = "Transaction type is required")
    private TransactionType transactionType;

    /**
     * Amount in the specified currency.
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 4,
            message = "Amount must have at most 15 integer digits " +
                    "and 4 decimal places")
    private BigDecimal amount;

    /**
     * ISO 4217 currency code. Must match the account's currency.
     */
    @NotBlank(message = "Currency code is required")
    @Size(min = 3, max = 3,
            message = "Currency code must be exactly 3 characters")
    private String currencyCode;

    /**
     * Optional description — e.g. "Cash deposit at branch" or
     * "ATM withdrawal authorised by teller"
     */
    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    /**
     * Client-generated reference for idempotency.
     */
    @NotBlank(message = "Transaction reference is required")
    @Size(max = 50,
            message = "Transaction reference must not exceed 50 characters")
    private String transactionReference;
}
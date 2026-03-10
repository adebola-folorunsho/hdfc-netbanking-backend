package com.hdfc.accountservice.account.dto;

import com.hdfc.accountservice.account.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new bank account.
 *
 * <p>Validated at the controller boundary via @Valid.
 * The service layer never receives invalid data — Bean Validation
 * rejects the request before it reaches service code.
 *
 * <p>userId is not included here — it is extracted from the JWT
 * principal in the controller. Users can only create accounts
 * for themselves. Admins and Tellers supply userId explicitly
 * via a separate admin endpoint.
 */
@Getter
@Builder
public class CreateAccountRequest {

    /**
     * The type of account to open.
     * Must be one of: SAVINGS, CURRENT, FIXED_DEPOSIT.
     */
    @NotNull(message = "Account type is required")
    private final AccountType accountType;

    /**
     * ISO 4217 currency code for this account.
     * Defaults to "NGN" if not supplied — enforced at service layer.
     * Length fixed at 3 characters per ISO 4217 standard.
     */
    @NotBlank(message = "Currency code is required")
    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
    private final String currencyCode;

    /**
     * Initial deposit amount for this account.
     * Must be greater than zero — you cannot open an account with
     * no funds. Minimum deposit rules per account type are enforced
     * at the service layer.
     */
    @NotNull(message = "Initial deposit amount is required")
    @DecimalMin(value = "0.01", message = "Initial deposit must be greater than zero")
    private final BigDecimal initialDeposit;

    /**
     * For FIXED_DEPOSIT accounts only — the maturity period in months.
     * Ignored for SAVINGS and CURRENT accounts.
     * Null is valid for non-FD accounts.
     */
    private final Integer maturityPeriodMonths;
}
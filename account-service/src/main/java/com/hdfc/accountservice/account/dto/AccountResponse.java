package com.hdfc.accountservice.account.dto;

import com.hdfc.accountservice.account.AccountStatus;
import com.hdfc.accountservice.account.AccountType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO representing a bank account.
 *
 * <p>Returned by all account endpoints — creation, retrieval, and updates.
 * Maps from the Account entity at the service layer.
 * The entity itself never crosses the service boundary.
 *
 * <p>Balance is included here for convenience — the dedicated
 * /balance endpoint exists for high-frequency balance reads
 * that hit the Redis cache directly without loading the full entity.
 */
@Getter
@Builder
public class AccountResponse {

    private final Long id;
    private final Long userId;
    private final String accountNumber;
    private final AccountType accountType;
    private final AccountStatus status;
    private final BigDecimal balance;
    private final String currencyCode;
    private final BigDecimal minimumBalance;
    private final BigDecimal interestRate;
    private final LocalDateTime maturityDate;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
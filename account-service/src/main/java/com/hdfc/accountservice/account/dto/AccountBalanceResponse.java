package com.hdfc.accountservice.account.dto;

import com.hdfc.accountservice.account.AccountStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lightweight response DTO for balance-only queries.
 *
 * <p>Used by the GET /api/v1/accounts/{id}/balance endpoint which
 * is called by Transaction Service before every transfer to verify
 * sufficient funds. This endpoint hits the Redis Write-Through cache
 * directly — returning the full AccountResponse would serialise
 * unnecessary fields and waste cache memory.
 *
 * <p>Keeping this DTO lean is a deliberate performance decision —
 * Transaction Service only needs the balance, currency, and account
 * status to make a transfer decision.
 */
@Getter
@Builder
public class AccountBalanceResponse {

    private final Long accountId;
    private final String accountNumber;
    private final BigDecimal balance;
    private final String currencyCode;
    private final AccountStatus status;
    private final LocalDateTime asOf;
}
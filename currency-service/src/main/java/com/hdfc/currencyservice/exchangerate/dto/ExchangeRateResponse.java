package com.hdfc.currencyservice.exchangerate.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO returned by ExchangeRateService to callers.
 *
 * <p>Never exposes internal cache or API response structures —
 * this is the clean contract at the service boundary.</p>
 *
 * <p>Immutable by design — all fields set at construction via Builder.
 * Banking data must never be mutated after creation.</p>
 *
 * @see com.hdfc.currencyservice.exchangerate.ExchangeRateService
 */
@Getter
@Builder
public class ExchangeRateResponse {

    /** The source currency code (ISO 4217). e.g. NGN */
    private final String fromCurrency;

    /** The target currency code (ISO 4217). e.g. USD */
    private final String toCurrency;

    /**
     * The exchange rate: 1 unit of fromCurrency = rate units of toCurrency.
     * BigDecimal — monetary precision is non-negotiable in a banking system.
     */
    private final BigDecimal rate;

    /** UTC timestamp of when this rate was fetched from ExchangeRate-API. */
    private final Instant fetchedAt;
}
package com.hdfc.currencyservice.exchangerate;

import com.hdfc.currencyservice.exchangerate.dto.ExchangeRateResponse;

/**
 * Contract for exchange rate retrieval operations.
 *
 * <p>DIP (Dependency Inversion Principle) — all callers depend on this
 * interface, never on the concrete implementation. This allows the
 * caching strategy or external API provider to be swapped without
 * touching any caller code.</p>
 *
 * <p>OCP (Open/Closed Principle) — new rate retrieval strategies
 * (e.g. a different provider, a fallback strategy) are added by
 * creating new implementations, never by modifying this interface.</p>
 */
public interface ExchangeRateService {

    /**
     * Returns the exchange rate for the given currency pair.
     *
     * <p>Read-Through caching strategy: rate is served from Redis if
     * present; fetched from ExchangeRate-API and cached on cache miss.</p>
     *
     * @param fromCurrency the source currency code (ISO 4217), e.g. "NGN"
     * @param toCurrency   the target currency code (ISO 4217), e.g. "USD"
     * @return             the exchange rate response containing the rate
     *                     and metadata
     * @throws com.hdfc.currencyservice.exchangerate.exception.ExchangeRateNotFoundException
     *                     if the rate cannot be retrieved from cache or API
     * @throws IllegalArgumentException if either currency code is blank
     */
    ExchangeRateResponse getExchangeRate(String fromCurrency, String toCurrency);
}
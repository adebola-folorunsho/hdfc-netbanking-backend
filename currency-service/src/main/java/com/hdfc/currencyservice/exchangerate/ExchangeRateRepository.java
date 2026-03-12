package com.hdfc.currencyservice.exchangerate;

import com.hdfc.currencyservice.exchangerate.dto.ExchangeRateResponse;

import java.util.Optional;

/**
 * Contract for exchange rate persistence operations.
 *
 * <p>DIP (Dependency Inversion Principle) — ExchangeRateServiceImpl
 * depends on this interface, not on RedisTemplate directly. This means
 * the caching backend (Redis, in-memory, etc.) can be swapped without
 * touching service logic.</p>
 *
 * <p>ISP (Interface Segregation Principle) — this interface is focused
 * solely on rate storage and retrieval. It does not mix in unrelated
 * concerns like API calls or conversion logic.</p>
 */
public interface ExchangeRateRepository {

    /**
     * Retrieves a cached exchange rate for the given currency pair.
     *
     * @param fromCurrency the source currency code (ISO 4217)
     * @param toCurrency   the target currency code (ISO 4217)
     * @return             an Optional containing the rate if cached,
     *                     or Optional.empty() on cache miss
     */
    Optional<ExchangeRateResponse> findRate(String fromCurrency, String toCurrency);

    /**
     * Stores an exchange rate in the cache with a 1-hour TTL.
     *
     * @param rate the exchange rate response to cache
     */
    void saveRate(ExchangeRateResponse rate);
}
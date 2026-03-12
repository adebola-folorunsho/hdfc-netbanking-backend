package com.hdfc.currencyservice.exchangerate;

import com.hdfc.currencyservice.exchangerate.dto.ExchangeRateResponse;
import com.hdfc.currencyservice.exchangerate.exception.ExchangeRateNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Implementation of ExchangeRateService.
 *
 * <p>Design Pattern: Read-Through Cache (Strategy Pattern)
 * Chosen over Cache-Aside because the caller should never need to know
 * whether the rate came from Redis or the external API. The cache is
 * transparent — the service always returns a rate or throws, never
 * returning null or requiring the caller to populate the cache.</p>
 *
 * <p>SRP: this class is solely responsible for coordinating rate
 * retrieval between the cache layer and the external API client.
 * It does not handle HTTP, Redis operations, or response mapping —
 * those responsibilities belong to their own classes.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateApiClient exchangeRateApiClient;

    /**
     * Returns the exchange rate for the given currency pair.
     *
     * <p>Read-Through flow:
     * 1. Validate inputs — fail fast on blank currencies
     * 2. Same-currency shortcut — always 1:1, no cache or API call needed
     * 3. Check Redis cache — return immediately on hit
     * 4. On cache miss — fetch from ExchangeRate-API, cache result, return
     * </p>
     *
     * @param fromCurrency the source currency code (ISO 4217)
     * @param toCurrency   the target currency code (ISO 4217)
     * @return             the exchange rate response
     * @throws IllegalArgumentException        if either currency code is blank
     * @throws ExchangeRateNotFoundException   if the rate cannot be retrieved
     */
    @Override
    public ExchangeRateResponse getExchangeRate(String fromCurrency, String toCurrency) {
        validateCurrencyCode(fromCurrency, "fromCurrency");
        validateCurrencyCode(toCurrency, "toCurrency");

        // Same-currency shortcut — no external call needed, rate is always 1:1
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return buildSameCurrencyRate(fromCurrency);
        }

        // Read-Through: check cache first
        return exchangeRateRepository.findRate(fromCurrency, toCurrency)
                .orElseGet(() -> fetchFromApiAndCache(fromCurrency, toCurrency));
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers — each does exactly one thing (SRP at method level)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Validates that a currency code is non-null and non-blank.
     * Fails fast at the entry point — never propagates invalid state.
     */
    private void validateCurrencyCode(String currencyCode, String fieldName) {
        Objects.requireNonNull(currencyCode, fieldName + " must not be null");
        if (currencyCode.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    /**
     * Builds a 1:1 rate response for same-currency conversions.
     * No cache or API call needed — mathematically guaranteed to be 1.
     */
    private ExchangeRateResponse buildSameCurrencyRate(String currency) {
        return ExchangeRateResponse.builder()
                .fromCurrency(currency)
                .toCurrency(currency)
                .rate(BigDecimal.ONE)
                .fetchedAt(Instant.now())
                .build();
    }

    /**
     * Fetches the rate from ExchangeRate-API on cache miss,
     * stores it in Redis, and returns it to the caller.
     *
     * <p>Cache miss is logged at INFO level so it is visible in
     * Railway logs without being noisy in normal operation.</p>
     */
    private ExchangeRateResponse fetchFromApiAndCache(String fromCurrency, String toCurrency) {
        log.info("Cache miss for {}->{} — fetching from ExchangeRate-API", fromCurrency, toCurrency);

        ExchangeRateResponse rate = exchangeRateApiClient.fetchRate(fromCurrency, toCurrency)
                .orElseThrow(() -> new ExchangeRateNotFoundException(fromCurrency, toCurrency));

        exchangeRateRepository.saveRate(rate);

        log.info("Rate {}->{} fetched and cached: {}", fromCurrency, toCurrency, rate.getRate());

        return rate;
    }
}
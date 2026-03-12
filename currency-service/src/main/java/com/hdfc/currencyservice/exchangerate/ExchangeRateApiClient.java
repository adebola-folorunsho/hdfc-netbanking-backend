package com.hdfc.currencyservice.exchangerate;

import com.hdfc.currencyservice.exchangerate.dto.ExchangeRateResponse;

import java.util.Optional;

/**
 * Contract for fetching live exchange rates from an external API.
 *
 * <p>DIP (Dependency Inversion Principle) — ExchangeRateServiceImpl
 * depends on this interface, not on the WebClient implementation directly.
 * This means the external provider (ExchangeRate-API, Open Exchange Rates,
 * etc.) can be swapped without touching service logic.</p>
 *
 * <p>OCP (Open/Closed Principle) — a fallback provider or mock
 * implementation is added by creating a new class that implements
 * this interface, never by modifying existing code.</p>
 */
public interface ExchangeRateApiClient {

    /**
     * Fetches the live exchange rate for the given currency pair
     * from the external ExchangeRate-API.
     *
     * @param fromCurrency the source currency code (ISO 4217)
     * @param toCurrency   the target currency code (ISO 4217)
     * @return             an Optional containing the rate if successfully
     *                     fetched, or Optional.empty() on API failure
     */
    Optional<ExchangeRateResponse> fetchRate(String fromCurrency, String toCurrency);
}
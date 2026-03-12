package com.hdfc.currencyservice.exchangerate.exception;

/**
 * Thrown when an exchange rate for the requested currency pair
 * cannot be found in the Redis cache or from ExchangeRate-API.
 *
 * <p>Extends RuntimeException — unchecked, consistent with the
 * exception strategy used across all HDFC NetBanking services.</p>
 */
public class ExchangeRateNotFoundException extends RuntimeException {

    /**
     * @param fromCurrency the source currency code that was requested
     * @param toCurrency   the target currency code that was requested
     */
    public ExchangeRateNotFoundException(String fromCurrency, String toCurrency) {
        super(String.format(
                "Exchange rate not found for currency pair: %s -> %s",
                fromCurrency,
                toCurrency
        ));
    }
}
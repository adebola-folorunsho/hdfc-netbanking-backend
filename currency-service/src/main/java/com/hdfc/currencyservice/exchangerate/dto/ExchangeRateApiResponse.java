package com.hdfc.currencyservice.exchangerate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Internal DTO mapping the raw JSON response from ExchangeRate-API v6.
 *
 * <p>Example API response:
 * <pre>
 * {
 *   "result": "success",
 *   "base_code": "NGN",
 *   "target_code": "USD",
 *   "conversion_rate": 0.00065
 * }
 * </pre>
 * </p>
 *
 * <p>This class is intentionally package-private in scope of usage —
 * only ExchangeRateApiClientImpl maps from this DTO to ExchangeRateResponse.
 * Nothing outside the exchangerate package ever sees this class.
 * This prevents the external API's response structure from leaking
 * into the rest of the application.</p>
 *
 * <p>SRP: this class only exists to deserialise the API JSON response.
 * No business logic lives here.</p>
 */
@Getter
@NoArgsConstructor
public class ExchangeRateApiResponse {

    /**
     * Result of the API call — "success" or "error".
     * Always check this before reading any other fields.
     */
    @JsonProperty("result")
    private String result;

    /**
     * The base currency code returned by the API.
     * Should match the fromCurrency we requested.
     */
    @JsonProperty("base_code")
    private String baseCode;

    /**
     * The target currency code returned by the API.
     * Should match the toCurrency we requested.
     */
    @JsonProperty("target_code")
    private String targetCode;

    /**
     * The conversion rate: 1 unit of base = conversionRate units of target.
     * double here because this is raw JSON deserialisation —
     * immediately converted to BigDecimal in ExchangeRateApiClientImpl
     * before use in any business logic.
     */
    @JsonProperty("conversion_rate")
    private double conversionRate;
}
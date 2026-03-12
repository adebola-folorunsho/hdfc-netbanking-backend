package com.hdfc.currencyservice.exchangerate;

import com.hdfc.currencyservice.exchangerate.dto.ExchangeRateApiResponse;
import com.hdfc.currencyservice.exchangerate.dto.ExchangeRateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * WebClient-based implementation of ExchangeRateApiClient.
 *
 * <p>Calls the ExchangeRate-API v6 endpoint:
 * GET https://v6.exchangerate-api.com/v6/{apiKey}/pair/{from}/{to}
 * NGN is the platform base currency per architectural decision.</p>
 *
 * <p>SRP: this class is solely responsible for making the HTTP call
 * to ExchangeRate-API and mapping the raw response to our internal DTO.
 * Caching, retry logic, and business rules live in other classes.</p>
 *
 * <p>Returns Optional.empty() on any API failure — never throws.
 * The service layer decides how to handle unavailability.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeRateApiClientImpl implements ExchangeRateApiClient {

    private final WebClient webClient;

    @Value("${exchangerate.api.key}")
    private String apiKey;

    @Value("${exchangerate.api.base-url}")
    private String baseUrl;

    /**
     * {@inheritDoc}
     *
     * <p>On any HTTP error or network failure, logs the error and
     * returns Optional.empty() — never propagates WebClient exceptions
     * to the service layer. The service layer will throw
     * ExchangeRateNotFoundException if Optional.empty() is returned.</p>
     */
    @Override
    public Optional<ExchangeRateResponse> fetchRate(String fromCurrency, String toCurrency) {
        String url = buildUrl(fromCurrency, toCurrency);

        log.info("Calling ExchangeRate-API: {}", sanitiseUrl(url));

        try {
            ExchangeRateApiResponse apiResponse = webClient
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(ExchangeRateApiResponse.class)
                    .block();

            if (apiResponse == null || !isSuccessResponse(apiResponse)) {
                log.warn("ExchangeRate-API returned invalid response for {}->{}", fromCurrency, toCurrency);
                return Optional.empty();
            }

            return Optional.of(mapToResponse(fromCurrency, toCurrency, apiResponse));

        } catch (Exception exception) {
            log.error("ExchangeRate-API call failed for {}->{}. Reason: {}",
                    fromCurrency, toCurrency, exception.getMessage());
            return Optional.empty();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Builds the full API URL for the given currency pair.
     * Format: {baseUrl}/{apiKey}/pair/{from}/{to}
     */
    private String buildUrl(String fromCurrency, String toCurrency) {
        return baseUrl + "/" + apiKey + "/pair/"
                + fromCurrency.toUpperCase() + "/"
                + toCurrency.toUpperCase();
    }

    /**
     * Strips the API key from the URL before logging.
     * Never log API keys — treat them as secrets.
     */
    private String sanitiseUrl(String url) {
        return url.replace(apiKey, "***");
    }

    /**
     * Checks whether the API response indicates a successful rate lookup.
     * ExchangeRate-API uses "result": "success" in its response body.
     */
    private boolean isSuccessResponse(ExchangeRateApiResponse apiResponse) {
        return "success".equalsIgnoreCase(apiResponse.getResult());
    }

    /**
     * Maps the raw ExchangeRate-API response to our internal DTO.
     * Keeps the API response structure internal to this class —
     * nothing outside this class knows about ExchangeRateApiResponse.
     */
    private ExchangeRateResponse mapToResponse(
            String fromCurrency,
            String toCurrency,
            ExchangeRateApiResponse apiResponse) {

        return ExchangeRateResponse.builder()
                .fromCurrency(fromCurrency.toUpperCase())
                .toCurrency(toCurrency.toUpperCase())
                .rate(BigDecimal.valueOf(apiResponse.getConversionRate()))
                .fetchedAt(Instant.now())
                .build();
    }
}
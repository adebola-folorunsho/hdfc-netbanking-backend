package com.hdfc.currencyservice.exchangerate;

import com.hdfc.currencyservice.common.ApiResponse;
import com.hdfc.currencyservice.exchangerate.dto.ExchangeRateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing exchange rate endpoints.
 *
 * <p>SRP: this controller only receives HTTP requests, delegates to
 * ExchangeRateService, and returns HTTP responses wrapped in
 * ApiResponse<T> for frontend consistency. No business logic here.</p>
 *
 * <p>All endpoints prefixed with /api/v1/currency per the
 * platform-wide REST versioning convention.</p>
 *
 * <p>All endpoints are public — no JWT required. Currency rates are
 * read-only, non-sensitive data. Routed through API Gateway (8080).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/currency")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    /**
     * Hardcoded list of supported currency codes (ISO 4217).
     * NGN is the platform base currency and is always first.
     * Expanding this list requires a code change — intentional per YAGNI.
     * Dynamic currency discovery from ExchangeRate-API is deferred.
     */
    private static final List<String> SUPPORTED_CURRENCIES = List.of(
            "NGN", "USD", "GBP", "EUR", "CAD", "AUD", "JPY", "GHS", "KES", "ZAR"
    );

    /**
     * Returns the exchange rate for a given currency pair.
     *
     * <p>Example: GET /api/v1/currency/rates/NGN/USD</p>
     *
     * @param fromCurrency the source currency code (ISO 4217)
     * @param toCurrency   the target currency code (ISO 4217)
     * @return             200 OK with ApiResponse wrapping ExchangeRateResponse
     */
    @GetMapping("/rates/{fromCurrency}/{toCurrency}")
    public ResponseEntity<ApiResponse<ExchangeRateResponse>> getExchangeRate(
            @PathVariable String fromCurrency,
            @PathVariable String toCurrency) {

        log.info("Exchange rate request: {}->{}", fromCurrency, toCurrency);

        ExchangeRateResponse rate = exchangeRateService.getExchangeRate(
                fromCurrency, toCurrency);

        return ResponseEntity.ok(ApiResponse.success(rate));
    }

    /**
     * Returns the hardcoded list of supported currency codes.
     *
     * <p>No caching, no external API call — returns a static list.
     * Used by the frontend to populate currency selection dropdowns.</p>
     *
     * <p>Example: GET /api/v1/currency/supported</p>
     *
     * @return 200 OK with ApiResponse wrapping list of currency codes
     */
    @GetMapping("/supported")
    public ResponseEntity<ApiResponse<List<String>>> getSupportedCurrencies() {
        log.info("Supported currencies request");
        return ResponseEntity.ok(ApiResponse.success(SUPPORTED_CURRENCIES));
    }
}
package com.hdfc.currencyservice.exchangerate;

import com.hdfc.currencyservice.exchangerate.dto.ExchangeRateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing exchange rate endpoints.
 *
 * <p>SRP: this controller only receives HTTP requests, delegates to
 * ExchangeRateService, and returns HTTP responses. No business logic
 * lives here — controllers are thin by design.</p>
 *
 * <p>All endpoints are prefixed with /api/v1/currency per the
 * platform-wide REST versioning convention.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/currency")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    /**
     * Returns the exchange rate for a given currency pair.
     *
     * <p>Currency codes must be ISO 4217 format e.g. NGN, USD, EUR.
     * NGN is the platform base currency.</p>
     *
     * <p>Example: GET /api/v1/currency/rates/NGN/USD</p>
     *
     * @param fromCurrency the source currency code (ISO 4217)
     * @param toCurrency   the target currency code (ISO 4217)
     * @return             200 OK with ExchangeRateResponse body
     */
    @GetMapping("/rates/{fromCurrency}/{toCurrency}")
    public ResponseEntity<ExchangeRateResponse> getExchangeRate(
            @PathVariable String fromCurrency,
            @PathVariable String toCurrency) {

        log.info("Exchange rate request: {}->{}", fromCurrency, toCurrency);

        ExchangeRateResponse response = exchangeRateService.getExchangeRate(
                fromCurrency,
                toCurrency
        );

        return ResponseEntity.ok(response);
    }
}
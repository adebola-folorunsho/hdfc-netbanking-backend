package com.hdfc.currencyservice.exchangerate;

import com.hdfc.currencyservice.common.ApiResponse;
import com.hdfc.currencyservice.exchangerate.dto.ExchangeRateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeRateController Unit Tests")
class ExchangeRateControllerTest {

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private ExchangeRateController exchangeRateController;

    private static final String FROM = "NGN";
    private static final String TO = "USD";
    private static final BigDecimal RATE = new BigDecimal("0.00065");

    private ExchangeRateResponse exchangeRateResponse;

    @BeforeEach
    void setUp() {
        exchangeRateResponse = ExchangeRateResponse.builder()
                .fromCurrency(FROM)
                .toCurrency(TO)
                .rate(RATE)
                .fetchedAt(Instant.now())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────
    // getExchangeRate tests
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return 200 with ApiResponse wrapping ExchangeRateResponse")
    void shouldReturn200_withApiResponseWrappingExchangeRateResponse() {
        // Arrange
        when(exchangeRateService.getExchangeRate(FROM, TO)).thenReturn(exchangeRateResponse);

        // Act
        ResponseEntity<ApiResponse<ExchangeRateResponse>> response =
                exchangeRateController.getExchangeRate(FROM, TO);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().getFromCurrency()).isEqualTo(FROM);
        assertThat(response.getBody().getData().getToCurrency()).isEqualTo(TO);
        assertThat(response.getBody().getData().getRate()).isEqualByComparingTo(RATE);

        verify(exchangeRateService, times(1)).getExchangeRate(FROM, TO);
    }

    // ─────────────────────────────────────────────────────────────────
    // getSupportedCurrencies tests
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return 200 with list of supported currencies")
    void shouldReturn200_withListOfSupportedCurrencies() {
        // Act
        ResponseEntity<ApiResponse<List<String>>> response =
                exchangeRateController.getSupportedCurrencies();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData()).contains("NGN", "USD", "GBP", "EUR");
        assertThat(response.getBody().getData()).hasSize(10);

        // No service call — hardcoded list, no external dependency
        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @DisplayName("Should always include NGN in supported currencies")
    void shouldAlwaysIncludeNGN_inSupportedCurrencies() {
        // Act
        ResponseEntity<ApiResponse<List<String>>> response =
                exchangeRateController.getSupportedCurrencies();

        // Assert — NGN is the platform base currency, must always be present
        assertThat(response.getBody().getData()).contains("NGN");
    }
}
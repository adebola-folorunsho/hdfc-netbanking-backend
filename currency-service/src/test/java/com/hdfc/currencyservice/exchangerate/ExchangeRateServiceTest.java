package com.hdfc.currencyservice.exchangerate;

import com.hdfc.currencyservice.exchangerate.dto.ExchangeRateResponse;
import com.hdfc.currencyservice.exchangerate.exception.ExchangeRateNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeRateService Unit Tests")
class ExchangeRateServiceTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private ExchangeRateApiClient exchangeRateApiClient;

    @InjectMocks
    private ExchangeRateServiceImpl exchangeRateService;

    private static final String BASE_CURRENCY = "NGN";
    private static final String TARGET_CURRENCY = "USD";
    private static final BigDecimal RATE = new BigDecimal("0.00065");

    @BeforeEach
    void setUp() {
        // Each test gets a clean mock state — no shared state between tests
    }

    @Test
    @DisplayName("Should return exchange rate when rate exists in cache")
    void shouldReturnExchangeRate_whenRateExistsInCache() {
        // Arrange
        ExchangeRateResponse cachedRate = ExchangeRateResponse.builder()
                .fromCurrency(BASE_CURRENCY)
                .toCurrency(TARGET_CURRENCY)
                .rate(RATE)
                .build();

        when(exchangeRateRepository.findRate(BASE_CURRENCY, TARGET_CURRENCY))
                .thenReturn(Optional.of(cachedRate));

        // Act
        ExchangeRateResponse result = exchangeRateService.getExchangeRate(BASE_CURRENCY, TARGET_CURRENCY);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getFromCurrency()).isEqualTo(BASE_CURRENCY);
        assertThat(result.getToCurrency()).isEqualTo(TARGET_CURRENCY);
        assertThat(result.getRate()).isEqualByComparingTo(RATE);

        // Verify repository was called exactly once — no duplicate fetches
        verify(exchangeRateRepository, times(1)).findRate(BASE_CURRENCY, TARGET_CURRENCY);
    }

    @Test
    @DisplayName("Should throw ExchangeRateNotFoundException when rate does not exist")
    void shouldThrowExchangeRateNotFoundException_whenRateDoesNotExist() {
        // Arrange
        when(exchangeRateRepository.findRate(BASE_CURRENCY, TARGET_CURRENCY))
                .thenReturn(Optional.empty());

        // API client also returns empty — simulates total rate unavailability
        when(exchangeRateApiClient.fetchRate(BASE_CURRENCY, TARGET_CURRENCY))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> exchangeRateService.getExchangeRate(BASE_CURRENCY, TARGET_CURRENCY))
                .isInstanceOf(ExchangeRateNotFoundException.class)
                .hasMessageContaining(BASE_CURRENCY)
                .hasMessageContaining(TARGET_CURRENCY);

        verify(exchangeRateRepository, times(1)).findRate(BASE_CURRENCY, TARGET_CURRENCY);
        verify(exchangeRateApiClient, times(1)).fetchRate(BASE_CURRENCY, TARGET_CURRENCY);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when fromCurrency is blank")
    void shouldThrowIllegalArgumentException_whenFromCurrencyIsBlank() {
        assertThatThrownBy(() -> exchangeRateService.getExchangeRate("", TARGET_CURRENCY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when toCurrency is blank")
    void shouldThrowIllegalArgumentException_whenToCurrencyIsBlank() {
        assertThatThrownBy(() -> exchangeRateService.getExchangeRate(BASE_CURRENCY, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should return rate of 1.0 when fromCurrency and toCurrency are the same")
    void shouldReturnRateOfOne_whenFromAndToCurrencyAreTheSame() {
        // Act
        ExchangeRateResponse result = exchangeRateService.getExchangeRate("NGN", "NGN");

        // Assert
        assertThat(result.getRate()).isEqualByComparingTo(BigDecimal.ONE);

        // Repository must never be called for same-currency conversion
        verifyNoInteractions(exchangeRateRepository);
    }
}
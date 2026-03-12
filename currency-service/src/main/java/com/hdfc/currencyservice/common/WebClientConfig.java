package com.hdfc.currencyservice.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient configuration for Currency Service.
 *
 * <p>A single shared WebClient bean is registered here and injected
 * into ExchangeRateApiClientImpl. This avoids creating a new
 * WebClient instance per request — WebClient is designed to be
 * shared and is thread-safe.</p>
 *
 * <p>No base URL is set here — the full URL is constructed in
 * ExchangeRateApiClientImpl from the baseUrl and apiKey properties.
 * This keeps the WebClient bean generic and reusable.</p>
 */
@Configuration
public class WebClientConfig {

    /**
     * Shared WebClient bean for all outbound HTTP calls.
     *
     * @return a new WebClient instance with default settings
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }
}
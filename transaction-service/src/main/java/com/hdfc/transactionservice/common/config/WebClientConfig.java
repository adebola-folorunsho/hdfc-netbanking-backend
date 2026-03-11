package com.hdfc.transactionservice.common.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient configuration for Transaction Service.
 *
 * <p>The @LoadBalanced annotation on the WebClient.Builder bean
 * enables Spring Cloud LoadBalancer to intercept WebClient requests
 * with lb:// URIs and resolve them via Eureka service discovery.
 *
 * <p>Without @LoadBalanced, lb://account-service would not be
 * resolved and WebClient calls would fail with an UnknownHostException.
 *
 * <p>This builder is injected into AccountServiceClient and
 * PaystackClient. AccountServiceClient uses it with
 * lb://account-service base URL — load balanced.
 * PaystackClient uses it with https://api.paystack.co — direct,
 * no load balancing needed for external URLs.
 */
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
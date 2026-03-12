package com.hdfc.currencyservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Entry point for Currency Service.
 *
 * <p>Registers with Eureka Server (port 8761) on startup via
 * {@code @EnableDiscoveryClient}. Other services discover this
 * service as "currency-service" via Eureka for load-balanced calls.</p>
 *
 * <p>Port: 8087
 * Base package: com.hdfc.currencyservice</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class CurrencyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CurrencyServiceApplication.class, args);
    }
}
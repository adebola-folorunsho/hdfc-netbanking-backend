package com.hdfc.accountservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the Account Service microservice.
 *
 * <p>Responsibilities of this service:
 * <ul>
 *   <li>Account creation for Savings, Current, and Fixed Deposit types</li>
 *   <li>Balance management with Write-Through Redis caching (TTL: 30s)</li>
 *   <li>Exposing balance and account data to Transaction Service via REST</li>
 * </ul>
 *
 * <p>This service validates JWTs issued by User Service but never issues them.
 * All monetary values use {@link java.math.BigDecimal} with
 * {@link java.math.RoundingMode#HALF_EVEN} rounding, stored as DECIMAL(19,4)
 * in MySQL per banking precision requirements.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
@EnableJpaAuditing
@EnableAsync
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
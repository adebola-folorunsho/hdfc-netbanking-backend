package com.hdfc.transactionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the Transaction Service microservice.
 *
 * <p>Responsibilities of this service:
 * <ul>
 *   <li>ACID-compliant fund transfers between internal accounts</li>
 *   <li>Paystack payment gateway integration (NGN sandbox)</li>
 *   <li>Transaction history with filtering by date, type, and amount</li>
 *   <li>Multi-currency transfers via Currency Service (Phase 7)</li>
 *   <li>Kafka event publishing — TRANSACTION_CREATED, FRAUD_ALERT</li>
 * </ul>
 *
 * <p>This service is the Saga orchestrator for all fund movements.
 * It calls Account Service via REST (WebClient) for balance checks,
 * debits, and credits. Compensation logic lives here — Account Service
 * receives only atomic debit/credit commands.
 *
 * <p>All monetary values use {@link java.math.BigDecimal} with
 * {@link java.math.RoundingMode#HALF_EVEN} rounding stored as
 * DECIMAL(19,4) in MySQL. JSR-354 (Moneta) is used for multi-currency
 * conversion arithmetic only.
 *
 * <p>This service validates JWTs issued by User Service but never
 * issues tokens itself.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableJpaAuditing
@EnableKafka
@EnableAsync
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}
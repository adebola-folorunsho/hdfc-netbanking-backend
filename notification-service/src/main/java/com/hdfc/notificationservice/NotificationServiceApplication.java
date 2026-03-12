package com.hdfc.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Entry point for Notification Service.
 *
 * <p>Registers with Eureka Server (port 8761) on startup via
 * {@code @EnableDiscoveryClient}. Consumes transaction-events
 * Kafka topic and sends email/SMS notifications to customers.</p>
 *
 * <p>Port: 8085
 * Base package: com.hdfc.notificationservice</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
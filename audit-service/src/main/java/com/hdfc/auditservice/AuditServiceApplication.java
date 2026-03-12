package com.hdfc.auditservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Entry point for Audit Service.
 *
 * <p>Registers with Eureka Server (port 8761) on startup via
 * {@code @EnableDiscoveryClient}. Admin Gateway discovers this
 * service as "audit-service" for routing admin audit requests.</p>
 *
 * <p>Port: 8084
 * Base package: com.hdfc.auditservice</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
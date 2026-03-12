package com.hdfc.schedulerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for Scheduler Service.
 *
 * <p>@EnableScheduling activates Spring's @Scheduled annotation
 * processing. Without this, all @Scheduled cron jobs are silently
 * ignored — they compile and deploy but never execute.</p>
 *
 * <p>Registers with Eureka Server (port 8761) on startup via
 * {@code @EnableDiscoveryClient}. Admin Gateway discovers this
 * service as "scheduler-service" for routing admin requests.</p>
 *
 * <p>Port: 8086
 * Base package: com.hdfc.schedulerservice</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class SchedulerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerServiceApplication.class, args);
    }
}
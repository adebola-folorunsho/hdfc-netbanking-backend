package com.hdfc.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Entry point for the HDFC User Service.
 *
 * Responsibilities of this service:
 *  - User registration and KYC
 *  - JWT authentication and refresh token rotation
 *  - Two-Factor Authentication (2FA) via TOTP
 *  - Role-Based Access Control (RBAC) with CUSTOMER, TELLER, ADMIN roles
 */
@SpringBootApplication
@EnableDiscoveryClient
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
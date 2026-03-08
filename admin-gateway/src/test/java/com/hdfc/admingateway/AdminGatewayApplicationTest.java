package com.hdfc.admingateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=dGVzdFNlY3JldEtleUZvckFkbWluR2F0ZXdheVRlc3RpbmdLZXkxMjM0NTY=",
                "eureka.client.enabled=false",
                "eureka.client.register-with-eureka=false",
                "eureka.client.fetch-registry=false",
                "spring.cloud.gateway.discovery.locator.enabled=false"
        }
)
class AdminGatewayApplicationTest {

    @Test
    void contextLoads() {
    }
}
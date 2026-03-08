package com.hdfc.admingateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds jwt.* properties from application.yml into a strongly-typed bean.
 * Using ConfigurationProperties instead of @Value ensures properties are
 * bound eagerly at context startup and are testable via @SpringBootTest.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
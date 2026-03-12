package com.hdfc.currencyservice.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hdfc.currencyservice.exchangerate.dto.ExchangeRateResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for Currency Service.
 *
 * <p>Configures a typed RedisTemplate&lt;String, ExchangeRateResponse&gt;
 * so exchange rate objects are serialised as clean JSON in Redis —
 * not as Java binary (the default JdkSerializationRedisSerializer
 * produces unreadable binary blobs that break across JVM versions).</p>
 *
 * <p>Jackson is configured with JavaTimeModule so that Instant
 * (fetchedAt field on ExchangeRateResponse) serialises correctly
 * as an ISO-8601 string rather than a numeric timestamp array.</p>
 */
@Configuration
public class RedisConfig {

    /**
     * Typed RedisTemplate for ExchangeRateResponse objects.
     *
     * <p>Key serialiser: StringRedisSerializer — keys are stored as
     * plain strings e.g. "currency:rate:NGN:USD". Human-readable
     * and compatible with Redis CLI inspection.</p>
     *
     * <p>Value serialiser: Jackson2JsonRedisSerializer — values are
     * stored as JSON. Readable, version-safe, and inspectable via
     * Redis CLI without a Java deserialiser.</p>
     *
     * @param connectionFactory injected by Spring Boot auto-configuration
     *                          from application.yml Redis properties
     * @return configured RedisTemplate for exchange rate caching
     */
    @Bean
    public RedisTemplate<String, ExchangeRateResponse> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, ExchangeRateResponse> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Keys stored as plain strings — human-readable in Redis CLI
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Values stored as JSON — readable and version-safe
        Jackson2JsonRedisSerializer<ExchangeRateResponse> valueSerializer =
                buildJsonSerializer();

        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Builds a Jackson2JsonRedisSerializer with JavaTimeModule registered.
     * JavaTimeModule is required so Instant serialises as ISO-8601 string,
     * not as a numeric timestamp — consistent with ISO-8601 platform standard.
     */
    private Jackson2JsonRedisSerializer<ExchangeRateResponse> buildJsonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();

        // JavaTimeModule: handles Java 8+ date/time types (Instant, LocalDateTime etc.)
        objectMapper.registerModule(new JavaTimeModule());

        // Write Instant as ISO-8601 string, not as [seconds, nanos] array
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return new Jackson2JsonRedisSerializer<>(objectMapper, ExchangeRateResponse.class);
    }
}
package com.hdfc.accountservice.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis cache configuration for Account Service.
 *
 * <p>Configures the RedisCacheManager with:
 * <ul>
 *   <li>JSON serialisation via Jackson — human-readable in Redis,
 *       resistant to class refactoring unlike Java serialisation</li>
 *   <li>String key serialisation — readable cache keys in Redis CLI</li>
 *   <li>30-second TTL — Write-Through safety net for balance cache</li>
 *   <li>Null value caching disabled — null balances are errors,
 *       never silently cached and served</li>
 *   <li>JSR-310 support — LocalDateTime fields serialise correctly</li>
 * </ul>
 *
 * <p>DESIGN PATTERN — Factory:
 * This class acts as a factory for the CacheManager bean.
 * All cache infrastructure configuration is centralised here —
 * no cache settings are scattered across service classes.
 */
@Configuration
public class CacheConfig {

    /**
     * Configures and returns the primary CacheManager backed by Redis.
     *
     * <p>The CacheManager is used by @Cacheable, @CachePut, and
     * @CacheEvict annotations throughout Account Service.
     * Spring Boot autoconfiguration defers to this bean when present —
     * it takes precedence over the application.yml cache settings
     * for serialisation configuration.
     *
     * @param redisConnectionFactory the Redis connection factory
     *                               autoconfigured by Spring Boot
     * @return the configured RedisCacheManager
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration cacheConfig = buildCacheConfiguration();

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(cacheConfig)
                // Per-cache TTL overrides can be added here if needed.
                // e.g. .withCacheConfiguration("account:balance",
                //         buildCacheConfiguration().entryTtl(Duration.ofSeconds(30)))
                .build();
    }

    /**
     * Builds the default Redis cache configuration.
     *
     * <p>Extracted into its own method so per-cache configurations
     * can call it and override only what they need (e.g. TTL),
     * keeping all other settings consistent across all caches.
     *
     * @return the base RedisCacheConfiguration
     */
    private RedisCacheConfiguration buildCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                // 30 seconds — matches the Write-Through safety net
                // defined in application.yml and the project spec.
                .entryTtl(Duration.ofSeconds(30))

                // Never cache null values — a null AccountBalanceResponse
                // means something failed at the data layer. Caching it
                // would serve incorrect data to Transaction Service for
                // up to 30 seconds, potentially allowing invalid transfers.
                .disableCachingNullValues()

                // Serialise cache keys as plain strings.
                // Produces readable keys like "account:balance::100"
                // in Redis CLI — essential for debugging cache state.
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))

                // Serialise cache values as JSON via Jackson.
                // Human-readable in Redis CLI and safe across refactoring —
                // unlike Java serialisation which breaks on class renames
                // or serialVersionUID changes.
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(buildJsonSerializer()));
    }

    /**
     * Builds a Jackson JSON serialiser configured for Redis cache values.
     *
     * <p>Three critical configurations:
     * <ol>
     *   <li>JavaTimeModule — handles LocalDateTime serialisation.
     *       Without this, LocalDateTime fields throw
     *       InvalidDefinitionException when cached.</li>
     *   <li>WRITE_DATES_AS_TIMESTAMPS disabled — stores dates as
     *       ISO-8601 strings ("2026-03-09T15:00:00") not as arrays
     *       ([2026,3,9,15,0,0]) — human-readable and interoperable.</li>
     *   <li>activateDefaultTyping — embeds the Java class name in the
     *       JSON so Jackson knows which class to deserialise back to.
     *       Without this, Jackson cannot reconstruct the correct type
     *       from the cached JSON and throws ClassCastException.</li>
     * </ol>
     *
     * @return a configured GenericJackson2JsonRedisSerializer
     */
    private GenericJackson2JsonRedisSerializer buildJsonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Register JSR-310 module so LocalDateTime, LocalDate etc.
        // serialise correctly. Without this registration, any DTO
        // containing LocalDateTime fields will fail to cache.
        objectMapper.registerModule(new JavaTimeModule());

        // Store dates as ISO-8601 strings, not numeric timestamps.
        // "2026-03-09T15:00:00" is readable; [2026,3,9,15,0,0] is not.
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Embed type information in the JSON payload so Jackson can
        // reconstruct the correct Java class on deserialisation.
        // LaissezFaireSubTypeValidator permits all types — acceptable
        // here because we control what goes into the cache entirely.
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}
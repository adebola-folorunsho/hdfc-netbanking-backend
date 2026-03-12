package com.hdfc.currencyservice.exchangerate;

import com.hdfc.currencyservice.exchangerate.dto.ExchangeRateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed implementation of ExchangeRateRepository.
 *
 * <p>Design Pattern: Read-Through Cache (Repository pattern over Redis)
 * This class is the cache layer — it never calls the external API.
 * It only reads from and writes to Redis. The service layer decides
 * when to call this vs. the API client.</p>
 *
 * <p>Cache key pattern: currency:rate:{fromCurrency}:{toCurrency}
 * TTL: 1 hour — rates are considered stale after 60 minutes and will
 * be re-fetched from ExchangeRate-API on the next cache miss.</p>
 *
 * <p>SRP: this class is solely responsible for Redis read/write
 * operations on exchange rate data. No business logic lives here.</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisExchangeRateRepository implements ExchangeRateRepository {

    private final RedisTemplate<String, ExchangeRateResponse> redisTemplate;

    /**
     * TTL for cached exchange rates.
     * Rates are re-fetched from ExchangeRate-API after 1 hour.
     * Never cache without a TTL — unbounded cache growth exhausts memory.
     */
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    /**
     * Key prefix — consistent with the platform-wide cache key convention.
     * Full key: currency:rate:{fromCurrency}:{toCurrency}
     */
    private static final String KEY_PREFIX = "currency:rate:";

    /**
     * {@inheritDoc}
     *
     * <p>Returns Optional.empty() on cache miss — never returns null.
     * Null Object pattern: the absence of a value is represented
     * explicitly as Optional.empty(), not as a null reference.</p>
     */
    @Override
    public Optional<ExchangeRateResponse> findRate(String fromCurrency, String toCurrency) {
        String cacheKey = buildCacheKey(fromCurrency, toCurrency);
        ExchangeRateResponse cachedRate = redisTemplate.opsForValue().get(cacheKey);

        if (cachedRate == null) {
            log.debug("Cache miss — key: {}", cacheKey);
            return Optional.empty();
        }

        log.debug("Cache hit — key: {}", cacheKey);
        return Optional.of(cachedRate);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Always sets TTL on write — Redis keys without TTL grow
     * unboundedly and will exhaust memory in production.</p>
     */
    @Override
    public void saveRate(ExchangeRateResponse rate) {
        String cacheKey = buildCacheKey(rate.getFromCurrency(), rate.getToCurrency());
        redisTemplate.opsForValue().set(cacheKey, rate, CACHE_TTL);
        log.debug("Cached rate — key: {}, TTL: {}h", cacheKey, CACHE_TTL.toHours());
    }

    /**
     * Builds the Redis cache key for a currency pair.
     * Format: currency:rate:{fromCurrency}:{toCurrency}
     * e.g. currency:rate:NGN:USD
     */
    private String buildCacheKey(String fromCurrency, String toCurrency) {
        return KEY_PREFIX + fromCurrency.toUpperCase() + ":" + toCurrency.toUpperCase();
    }
}
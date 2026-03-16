package com.loopers.infrastructure.product;

import com.loopers.application.product.CachedBrandProductPage;
import com.loopers.application.product.CachedProductDetail;
import com.loopers.application.product.ProductCacheManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisProductCacheManager implements ProductCacheManager {

    private static final long LIST_BASE_TTL_SECONDS = 60;
    private static final long LIST_JITTER_BOUND = 10;
    private static final long DETAIL_BASE_TTL_SECONDS = 300;
    private static final long DETAIL_JITTER_BOUND = 30;
    private static final int EVICT_MAX_PAGE = 3;
    private static final int EVICT_DEFAULT_SIZE = 20;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<CachedBrandProductPage> getProductList(Long brandId, int page, int size) {
        return get(productListKey(brandId, page, size), CachedBrandProductPage.class);
    }

    @Override
    public void putProductList(Long brandId, int page, int size, CachedBrandProductPage value) {
        put(productListKey(brandId, page, size), value, listTtlWithJitter());
    }

    @Override
    public Optional<CachedProductDetail> getProductDetail(Long productId) {
        return get(productDetailKey(productId), CachedProductDetail.class);
    }

    @Override
    public void putProductDetail(Long productId, CachedProductDetail value) {
        put(productDetailKey(productId), value, detailTtlWithJitter());
    }

    @Override
    public void evictProductCaches(Long productId, Long brandId) {
        try {
            redisTemplate.delete(productDetailKey(productId));

            for (int page = 0; page < EVICT_MAX_PAGE; page++) {
                redisTemplate.delete(productListKey(brandId, page, EVICT_DEFAULT_SIZE));
            }
        } catch (Exception e) {
            log.warn("캐시 무효화 실패. productId={}, brandId={}", productId, brandId, e);
        }
    }

    private <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("Redis 조회 실패, DB fallback. key={}", key, e);
            return Optional.empty();
        }
    }

    private void put(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (JsonProcessingException e) {
            log.warn("캐시 직렬화 실패. key={}", key, e);
        } catch (Exception e) {
            log.warn("Redis 저장 실패. key={}", key, e);
        }
    }

    private String productListKey(Long brandId, int page, int size) {
        return "product:brand:" + brandId + ":page:" + page + ":size:" + size;
    }

    private String productDetailKey(Long productId) {
        return "product:detail:" + productId;
    }

    private Duration listTtlWithJitter() {
        long jitter = ThreadLocalRandom.current().nextLong(1, LIST_JITTER_BOUND + 1);
        return Duration.ofSeconds(LIST_BASE_TTL_SECONDS + jitter);
    }

    private Duration detailTtlWithJitter() {
        long jitter = ThreadLocalRandom.current().nextLong(1, DETAIL_JITTER_BOUND + 1);
        return Duration.ofSeconds(DETAIL_BASE_TTL_SECONDS + jitter);
    }
}

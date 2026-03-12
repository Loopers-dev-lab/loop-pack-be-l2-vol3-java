package com.loopers.infrastructure.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductCacheRepository;
import com.loopers.application.product.ProductPageResult;
import com.loopers.config.CacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * RedisTemplate을 직접 사용하는 캐시 구현체.
 * - 읽기: defaultRedisTemplate (REPLICA_PREFERRED) → Replica 분산 읽기
 * - 쓰기/삭제: masterRedisTemplate (MASTER) → 복제 지연(lag) 없이 즉시 반영
 * - 모든 예외를 내부에서 흡수 → Redis 장애 시 서비스 정상 동작 보장 (캐시 미스로 처리)
 */
@Slf4j
@Repository
public class ProductCacheRepositoryImpl implements ProductCacheRepository {

    private static final String CACHE_KEY_PATTERN = "loopers:product:list:*";

    private final RedisTemplate<String, String> readTemplate;
    private final RedisTemplate<String, String> writeTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;

    public ProductCacheRepositoryImpl(
            RedisTemplate<String, String> defaultRedisTemplate,
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
            ObjectMapper objectMapper,
            CacheProperties cacheProperties
    ) {
        this.readTemplate = defaultRedisTemplate;
        this.writeTemplate = masterRedisTemplate;
        this.objectMapper = objectMapper;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public Optional<ProductPageResult> getList(String cacheKey) {
        try {
            String json = readTemplate.opsForValue().get(cacheKey);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ProductPageResult.class));
        } catch (Exception e) {
            log.warn("상품 목록 캐시 조회 실패 (key={}): {}", cacheKey, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void saveList(String cacheKey, ProductPageResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            long ttlSeconds = cacheProperties.getTtlSeconds("product:list");
            writeTemplate.opsForValue().set(cacheKey, json, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("상품 목록 캐시 저장 실패 (key={}): {}", cacheKey, e.getMessage());
        }
    }

    @Override
    public void evictAll() {
        try {
            // KEYS는 O(N)으로 Redis를 블로킹하므로, SCAN으로 안전하게 순회
            writeTemplate.execute((RedisCallback<Void>) connection -> {
                ScanOptions options = ScanOptions.scanOptions()
                        .match(CACHE_KEY_PATTERN)
                        .count(100)
                        .build();
                try (var cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        connection.del(cursor.next());
                    }
                } catch (Exception e) {
                    log.warn("캐시 무효화 scan 오류: {}", e.getMessage());
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("상품 목록 캐시 무효화 실패: {}", e.getMessage());
        }
    }
}

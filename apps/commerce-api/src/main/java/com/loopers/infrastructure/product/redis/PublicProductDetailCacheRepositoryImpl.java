package com.loopers.infrastructure.product.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.cache.PublicProductDetailCacheRepository;
import com.loopers.application.product.view.ProductView;
import com.loopers.config.redis.RedisConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@Slf4j
@RequiredArgsConstructor
public class PublicProductDetailCacheRepositoryImpl implements PublicProductDetailCacheRepository {

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<ProductView> findByKey(String cacheKey) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null || cached.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(read(cached));
        } catch (RuntimeException exception) {
            log.warn("public product detail cache read failed. key={}", cacheKey, exception);
            return Optional.empty();
        }
    }

    @Override
    public void save(String cacheKey, ProductView value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(cacheKey, write(value), ttl);
        } catch (RuntimeException exception) {
            log.warn("public product detail cache save failed. key={}, ttl={}", cacheKey, ttl, exception);
        }
    }

    @Override
    public void evict(String cacheKey) {
        try {
            redisTemplate.delete(cacheKey);
        } catch (RuntimeException exception) {
            log.warn("public product detail cache eviction failed. key={}", cacheKey, exception);
        }
    }

    private ProductView read(String raw) {
        try {
            return objectMapper.readValue(raw, ProductView.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("상품 상세 Redis 데이터 역직렬화에 실패했습니다.", e);
        }
    }

    private String write(ProductView view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("상품 상세 Redis 데이터 직렬화에 실패했습니다.", e);
        }
    }
}

package com.loopers.infrastructure.ranking.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.ranking.cache.RankingProductCacheItem;
import com.loopers.application.ranking.cache.RankingProductCacheRepository;
import com.loopers.config.redis.RedisConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Repository
@Slf4j
@RequiredArgsConstructor
public class RankingProductCacheRepositoryImpl implements RankingProductCacheRepository {

    private static final String CACHE_KEY_PREFIX = "ranking:product:v1:";

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Map<UUID, RankingProductCacheItem> findAll(Collection<UUID> productIds) {
        LinkedHashMap<UUID, RankingProductCacheItem> result = new LinkedHashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        try {
            Map<UUID, String> keyByProductId = new LinkedHashMap<>();
            for (UUID productId : productIds) {
                keyByProductId.put(productId, buildKey(productId));
            }
            java.util.List<String> cachedValues = redisTemplate.opsForValue().multiGet(keyByProductId.values());
            if (cachedValues == null) {
                return result;
            }
            int index = 0;
            for (UUID productId : keyByProductId.keySet()) {
                String cachedValue = cachedValues.get(index++);
                if (cachedValue == null || cachedValue.isBlank()) {
                    continue;
                }
                result.put(productId, read(cachedValue));
            }
            return result;
        } catch (RuntimeException exception) {
            log.warn("ranking product cache batch read failed. size={}", productIds.size(), exception);
            return Map.of();
        }
    }

    @Override
    public void saveAll(Collection<RankingProductCacheItem> items, Duration ttl) {
        if (items.isEmpty()) {
            return;
        }
        try {
            for (RankingProductCacheItem item : items) {
                redisTemplate.opsForValue().set(buildKey(item.productId()), write(item), ttl);
            }
        } catch (RuntimeException exception) {
            log.warn("ranking product cache batch save failed. size={}, ttl={}", items.size(), ttl, exception);
        }
    }

    @Override
    public void evict(UUID productId) {
        try {
            redisTemplate.delete(buildKey(productId));
        } catch (RuntimeException exception) {
            log.warn("ranking product cache eviction failed. productId={}", productId, exception);
        }
    }

    private String buildKey(UUID productId) {
        return CACHE_KEY_PREFIX + productId;
    }

    private RankingProductCacheItem read(String raw) {
        try {
            return objectMapper.readValue(raw, RankingProductCacheItem.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("랭킹 상품 캐시 역직렬화에 실패했습니다.", e);
        }
    }

    private String write(RankingProductCacheItem item) {
        try {
            return objectMapper.writeValueAsString(item);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("랭킹 상품 캐시 직렬화에 실패했습니다.", e);
        }
    }
}

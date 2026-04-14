package com.loopers.application.ranking;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RankingProductCache {

    private static final String KEY_PREFIX = "ranking:product::";
    private static final long SOFT_TTL_SECONDS = 60;
    private static final long HARD_TTL_SECONDS = 300;

    private final ProductRepository productRepository;
    private final RedisTemplate<String, byte[]> redisTemplate;
    private final GenericJackson2JsonRedisSerializer serializer;
    private final Set<Long> refreshingKeys = ConcurrentHashMap.newKeySet();

    public RankingProductCache(ProductRepository productRepository,
                               RedisConnectionFactory redisConnectionFactory) {
        this.productRepository = productRepository;
        this.serializer = new GenericJackson2JsonRedisSerializer();
        this.redisTemplate = buildRedisTemplate(redisConnectionFactory);
    }

    public Map<Long, CachedProductSnapshot> findAllByIds(List<Long> productDbIds) {
        if (productDbIds == null || productDbIds.isEmpty()) {
            return Map.of();
        }
        List<String> keys = new ArrayList<>(productDbIds.size());
        for (Long id : productDbIds) {
            keys.add(KEY_PREFIX + id);
        }

        List<byte[]> raw;
        try {
            raw = redisTemplate.opsForValue().multiGet(keys);
        } catch (RuntimeException e) {
            log.warn("랭킹 상품 캐시 multiGet 실패, DB 폴백 수행 size={}", productDbIds.size(), e);
            return fetchAndCacheBatch(productDbIds);
        }

        Map<Long, CachedProductSnapshot> result = new HashMap<>(productDbIds.size());
        List<Long> missingIds = new ArrayList<>();
        List<Long> staleIds = new ArrayList<>();
        for (int i = 0; i < productDbIds.size(); i++) {
            Long id = productDbIds.get(i);
            byte[] bytes = (raw == null || i >= raw.size()) ? null : raw.get(i);
            if (bytes == null) {
                missingIds.add(id);
                continue;
            }
            CachedProductSnapshot snapshot;
            try {
                snapshot = (CachedProductSnapshot) serializer.deserialize(bytes);
            } catch (RuntimeException e) {
                log.warn("랭킹 상품 캐시 역직렬화 실패 productDbId={}", id, e);
                missingIds.add(id);
                continue;
            }
            if (snapshot == null) {
                missingIds.add(id);
                continue;
            }
            result.put(id, snapshot);
            if (!isWithinSoftTtl(snapshot.cachedAtEpochSecond())) {
                staleIds.add(id);
            }
        }

        if (!missingIds.isEmpty()) {
            result.putAll(fetchAndCacheBatch(missingIds));
        }
        for (Long staleId : staleIds) {
            refreshAsync(staleId, KEY_PREFIX + staleId);
        }
        log.debug("랭킹 상품 캐시 조회 요청={} 히트={} 미스={} Stale={}",
                productDbIds.size(), productDbIds.size() - missingIds.size(), missingIds.size(), staleIds.size());
        return result;
    }

    private Map<Long, CachedProductSnapshot> fetchAndCacheBatch(List<Long> ids) {
        List<ProductModel> products;
        try {
            products = productRepository.findAllByIdIncludingDeleted(ids);
        } catch (RuntimeException e) {
            log.error("랭킹 상품 DB 배치 조회 실패 ids={}", ids, e);
            return Map.of();
        }
        Map<Long, CachedProductSnapshot> map = new HashMap<>(products.size());
        for (ProductModel product : products) {
            CachedProductSnapshot snapshot = CachedProductSnapshot.from(product);
            try {
                byte[] bytes = serializer.serialize(snapshot);
                redisTemplate.opsForValue().set(KEY_PREFIX + snapshot.id(), bytes, HARD_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (RuntimeException e) {
                log.warn("랭킹 상품 캐시 적재 실패 productDbId={}", snapshot.id(), e);
            }
            map.put(snapshot.id(), snapshot);
        }
        return map;
    }

    public CachedProductSnapshot findById(Long productDbId) {
        String key = KEY_PREFIX + productDbId;
        byte[] bytes = redisTemplate.opsForValue().get(key);

        if (bytes != null) {
            CachedProductSnapshot snapshot = (CachedProductSnapshot) serializer.deserialize(bytes);
            if (snapshot != null) {
                if (isWithinSoftTtl(snapshot.cachedAtEpochSecond())) {
                    return snapshot;
                }
                refreshAsync(productDbId, key);
                return snapshot;
            }
        }

        return fetchAndCache(productDbId, key);
    }

    private boolean isWithinSoftTtl(long cachedAtEpochSecond) {
        return cachedAtEpochSecond > 0
                && Instant.now().getEpochSecond() - cachedAtEpochSecond < SOFT_TTL_SECONDS;
    }

    private void refreshAsync(Long productDbId, String key) {
        if (!refreshingKeys.add(productDbId)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                fetchAndCache(productDbId, key);
            } catch (Exception e) {
                log.warn("백그라운드 캐시 갱신 실패 productDbId={}", productDbId, e);
            } finally {
                refreshingKeys.remove(productDbId);
            }
        });
    }

    private CachedProductSnapshot fetchAndCache(Long productDbId, String key) {
        return productRepository.findById(productDbId)
                .map(product -> {
                    CachedProductSnapshot snapshot = CachedProductSnapshot.from(product);
                    byte[] bytes = serializer.serialize(snapshot);
                    redisTemplate.opsForValue().set(key, bytes, HARD_TTL_SECONDS, TimeUnit.SECONDS);
                    return snapshot;
                })
                .orElse(null);
    }

    private RedisTemplate<String, byte[]> buildRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(RedisSerializer.byteArray());
        template.afterPropertiesSet();
        return template;
    }
}

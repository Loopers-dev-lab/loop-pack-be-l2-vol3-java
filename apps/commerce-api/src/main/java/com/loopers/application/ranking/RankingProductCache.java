package com.loopers.application.ranking;

import com.loopers.domain.product.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.time.Instant;
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

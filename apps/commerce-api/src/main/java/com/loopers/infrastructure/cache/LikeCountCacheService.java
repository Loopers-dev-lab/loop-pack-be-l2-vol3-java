package com.loopers.infrastructure.cache;

import com.loopers.config.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 좋아요 수 Redis 캐시 서비스.
 *
 * <h3>캐시 스탬피드 방지: PER (Probabilistic Early Recomputation)</h3>
 * <pre>
 * TTL이 임계값(EARLY_REFRESH_THRESHOLD) 이하로 남으면 확률적으로 갱신 트리거.
 *
 * 예: TTL 7일, 임계값 1일
 * - 남은 TTL > 1일: 캐시 반환
 * - 남은 TTL ≤ 1일: 확률적으로 갱신 필요 신호 반환
 *
 * 확률 공식: P(refresh) = 1 - (remainingTtl / threshold)
 * - 남은 1일: 0% 확률
 * - 남은 12시간: 50% 확률
 * - 남은 1시간: 96% 확률
 * </pre>
 *
 * <h3>샤딩 전략</h3>
 * <pre>
 * 키 패턴: {product:{shardId}}:like:v{version}:{productId}
 * shardId = productId % 16
 * </pre>
 *
 * <h3>TTL 전략</h3>
 * Long TTL (7일) + Overwrite on Write.
 */
@Slf4j
@Service
public class LikeCountCacheService {

    private static final int SHARD_COUNT = 16;
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);
    private static final Duration EARLY_REFRESH_THRESHOLD = Duration.ofDays(1);

    private final RedisTemplate<String, String> readTemplate;
    private final RedisTemplate<String, String> writeTemplate;
    private final int cacheVersion;

    public LikeCountCacheService(
            RedisTemplate<String, String> readTemplate,
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> writeTemplate,
            @Value("${cache.like-count.version:1}") int cacheVersion
    ) {
        this.readTemplate = readTemplate;
        this.writeTemplate = writeTemplate;
        this.cacheVersion = cacheVersion;
    }

    /**
     * 좋아요 수 조회 + PER 신호 반환.
     *
     * @param productId 상품 ID
     * @return CacheResult (값 + 갱신 필요 여부)
     */
    public CacheResult getWithRefreshSignal(Long productId) {
        try {
            String key = buildKey(productId);
            String value = readTemplate.opsForValue().get(key);

            if (value == null) {
                return CacheResult.miss();
            }

            long count = Long.parseLong(value);

            // TTL 확인하여 PER 판단
            Long ttlSeconds = readTemplate.getExpire(key, TimeUnit.SECONDS);
            if (ttlSeconds == null || ttlSeconds < 0) {
                return CacheResult.hit(count, false);
            }

            boolean shouldRefresh = shouldRefreshEarly(ttlSeconds);
            return CacheResult.hit(count, shouldRefresh);

        } catch (Exception e) {
            log.warn("Redis getWithRefreshSignal 실패: productId={}", productId, e);
            return CacheResult.miss();
        }
    }

    /**
     * PER 확률 계산.
     * 남은 TTL이 임계값 이하일 때 확률적으로 true 반환.
     *
     * @param ttlSeconds 남은 TTL (초)
     * @return 갱신 필요 여부
     */
    private boolean shouldRefreshEarly(long ttlSeconds) {
        long thresholdSeconds = EARLY_REFRESH_THRESHOLD.getSeconds();

        if (ttlSeconds > thresholdSeconds) {
            return false;
        }

        // 확률 = 1 - (remainingTtl / threshold)
        double probability = 1.0 - ((double) ttlSeconds / thresholdSeconds);
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    /**
     * 좋아요 수 증가 + 캐시 덮어쓰기.
     */
    public Long increment(Long productId) {
        try {
            String key = buildKey(productId);
            Long result = writeTemplate.opsForValue().increment(key);
            writeTemplate.opsForValue().set(key, String.valueOf(result), DEFAULT_TTL);
            markDirty(productId);
            return result;
        } catch (Exception e) {
            log.warn("Redis increment 실패: productId={}", productId, e);
            return null;
        }
    }

    /**
     * 좋아요 수 감소 + 캐시 덮어쓰기.
     */
    public Long decrement(Long productId) {
        try {
            String key = buildKey(productId);
            Long result = writeTemplate.opsForValue().decrement(key);
            long safeResult = result != null && result >= 0 ? result : 0L;
            writeTemplate.opsForValue().set(key, String.valueOf(safeResult), DEFAULT_TTL);
            markDirty(productId);
            return safeResult;
        } catch (Exception e) {
            log.warn("Redis decrement 실패: productId={}", productId, e);
            return null;
        }
    }

    /**
     * 좋아요 수 조회 (PER 없이 단순 조회).
     */
    public Optional<Long> get(Long productId) {
        try {
            String key = buildKey(productId);
            String value = readTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(value));
        } catch (Exception e) {
            log.warn("Redis get 실패: productId={}", productId, e);
            return Optional.empty();
        }
    }

    /**
     * 여러 상품의 좋아요 수 일괄 조회.
     */
    public Map<Long, Long> getMultiple(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }

        try {
            List<String> keys = productIds.stream()
                    .map(this::buildKey)
                    .toList();

            List<String> values = readTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return Map.of();
            }

            Map<Long, Long> result = new HashMap<>();
            for (int i = 0; i < productIds.size(); i++) {
                String value = values.get(i);
                if (value != null) {
                    result.put(productIds.get(i), Long.parseLong(value));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Redis multiGet 실패: productIds={}", productIds, e);
            return Map.of();
        }
    }

    /**
     * 좋아요 수 덮어쓰기 (Overwrite).
     */
    public void set(Long productId, Long count) {
        try {
            String key = buildKey(productId);
            writeTemplate.opsForValue().set(key, String.valueOf(count), DEFAULT_TTL);
        } catch (Exception e) {
            log.warn("Redis set 실패: productId={}, count={}", productId, count, e);
        }
    }

    /**
     * 여러 상품의 좋아요 수 일괄 덮어쓰기.
     */
    public void setMultiple(Map<Long, Long> counts) {
        if (counts == null || counts.isEmpty()) {
            return;
        }

        counts.forEach((productId, count) -> {
            try {
                String key = buildKey(productId);
                writeTemplate.opsForValue().set(key, String.valueOf(count), DEFAULT_TTL);
            } catch (Exception e) {
                log.warn("Redis set 실패: productId={}, count={}", productId, count, e);
            }
        });
    }

    /**
     * 변경된 상품 ID를 dirty set에 추가.
     */
    public void markDirty(Long productId) {
        try {
            String dirtyKey = buildDirtySetKey();
            writeTemplate.opsForSet().add(dirtyKey, String.valueOf(productId));
        } catch (Exception e) {
            log.warn("Redis markDirty 실패: productId={}", productId, e);
        }
    }

    /**
     * dirty 상품 ID 목록 조회.
     */
    public Set<Long> getDirtyProductIds() {
        try {
            String dirtyKey = buildDirtySetKey();
            Set<String> members = readTemplate.opsForSet().members(dirtyKey);
            if (members == null || members.isEmpty()) {
                return Set.of();
            }
            Set<Long> result = new HashSet<>();
            for (String member : members) {
                result.add(Long.parseLong(member));
            }
            return result;
        } catch (Exception e) {
            log.warn("Redis getDirtyProductIds 실패", e);
            return Set.of();
        }
    }

    /**
     * dirty 상품 ID 목록 초기화.
     */
    public void clearDirtyProductIds() {
        try {
            String dirtyKey = buildDirtySetKey();
            writeTemplate.delete(dirtyKey);
        } catch (Exception e) {
            log.warn("Redis clearDirtyProductIds 실패", e);
        }
    }

    /**
     * 특정 상품을 dirty set에서 제거.
     */
    public void removeDirty(Long productId) {
        try {
            String dirtyKey = buildDirtySetKey();
            writeTemplate.opsForSet().remove(dirtyKey, String.valueOf(productId));
        } catch (Exception e) {
            log.warn("Redis removeDirty 실패: productId={}", productId, e);
        }
    }

    public int getCacheVersion() {
        return cacheVersion;
    }

    private String buildKey(Long productId) {
        int shardId = (int) (productId % SHARD_COUNT);
        return String.format("{product:%d}:like:v%d:%d", shardId, cacheVersion, productId);
    }

    private String buildDirtySetKey() {
        return String.format("{product:dirty}:like:v%d", cacheVersion);
    }

    /**
     * 캐시 조회 결과.
     */
    public record CacheResult(
            boolean hit,
            Long value,
            boolean shouldRefresh
    ) {
        public static CacheResult miss() {
            return new CacheResult(false, null, true);
        }

        public static CacheResult hit(Long value, boolean shouldRefresh) {
            return new CacheResult(true, value, shouldRefresh);
        }
    }
}

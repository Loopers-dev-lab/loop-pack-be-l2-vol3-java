package com.loopers.batch.cache;

import com.loopers.config.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 배치 전용 좋아요 카운트 캐시 서비스.
 *
 * <h3>API 모듈과 동일한 샤딩/버저닝 전략 사용</h3>
 * <pre>
 * 키 패턴: {product:{shardId}}:like:v{version}:{productId}
 * TTL: 7일 (Long TTL + Overwrite)
 * </pre>
 */
@Slf4j
@Service
public class LikeCountBatchCacheService {

    private static final int SHARD_COUNT = 16;
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);

    private final RedisTemplate<String, String> readTemplate;
    private final RedisTemplate<String, String> writeTemplate;
    private final int cacheVersion;

    public LikeCountBatchCacheService(
            RedisTemplate<String, String> readTemplate,
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> writeTemplate,
            @Value("${cache.like-count.version:1}") int cacheVersion
    ) {
        this.readTemplate = readTemplate;
        this.writeTemplate = writeTemplate;
        this.cacheVersion = cacheVersion;
    }

    /**
     * dirty 상품 ID 목록 조회.
     *
     * @return dirty 상품 ID Set
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
     * 좋아요 수 덮어쓰기 (Overwrite).
     * DB 동기화 결과를 캐시에 저장.
     *
     * @param productId 상품 ID
     * @param count 좋아요 수
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
     *
     * @param counts 상품 ID → 좋아요 수 Map
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
     * 특정 상품을 dirty set에서 제거.
     *
     * @param productId 상품 ID
     */
    public void removeDirty(Long productId) {
        try {
            String dirtyKey = buildDirtySetKey();
            writeTemplate.opsForSet().remove(dirtyKey, String.valueOf(productId));
        } catch (Exception e) {
            log.warn("Redis removeDirty 실패: productId={}", productId, e);
        }
    }

    /**
     * dirty set 전체 삭제.
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
     * 샤딩된 키 생성.
     * API 모듈과 동일한 패턴 사용.
     */
    private String buildKey(Long productId) {
        int shardId = (int) (productId % SHARD_COUNT);
        return String.format("{product:%d}:like:v%d:%d", shardId, cacheVersion, productId);
    }

    /**
     * dirty set 키 생성.
     */
    private String buildDirtySetKey() {
        return String.format("{product:dirty}:like:v%d", cacheVersion);
    }
}

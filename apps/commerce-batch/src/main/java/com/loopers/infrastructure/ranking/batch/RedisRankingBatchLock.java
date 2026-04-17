package com.loopers.infrastructure.ranking.batch;

import com.loopers.domain.ranking.batch.RankingBatchJobParameters;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Objects;

/**
 * Redis SET NX 기반 period 락. Job 설정 클래스에서만 Bean으로 등록한다.
 */
public final class RedisRankingBatchLock {

    private static final Duration LOCK_TTL = Duration.ofHours(4);

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 생성자 주입.
     *
     * @param redisTemplate RedisTemplate
     */
    public RedisRankingBatchLock(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 락을 획득한다.
     *
     * @param period 기간
     * @param periodKey 기간 키
     * @param ownerToken 소유자 토큰
     * @return 락 획득 여부
     */
    public boolean tryAcquire(String period, String periodKey, String ownerToken) {
        Objects.requireNonNull(ownerToken, "ownerToken");
        String key = RankingBatchJobParameters.redisLockKey(period, periodKey);
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, ownerToken, LOCK_TTL);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 락을 해제한다.
     *
     * @param period 기간
     * @param periodKey 기간 키
     * @param ownerToken 소유자 토큰
     */
    public void releaseIfHeld(String period, String periodKey, String ownerToken) {
        if (ownerToken == null) {
            return;
        }
        String key = RankingBatchJobParameters.redisLockKey(period, periodKey);
        String current = redisTemplate.opsForValue().get(key);
        if (ownerToken.equals(current)) {
            redisTemplate.delete(key);
        }
    }
}

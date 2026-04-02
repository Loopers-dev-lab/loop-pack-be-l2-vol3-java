package com.loopers.infrastructure.orderqueue.redis;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.orderqueue.AdmissionStorageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class RedisAdmissionStorageRepository implements AdmissionStorageRepository {

    private static final String CLAIM_KEY_PREFIX = "order:admission:claim:";
    private static final String TOKEN_KEY_PREFIX = "order:admission:token:";
    private static final String ACTIVE_TOKEN_KEY = "order:admission:active:v1";

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean tryClaim(String memberId, long nowMillis, long claimTtlMillis) {
        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(
                claimKey(memberId),
                String.valueOf(nowMillis),
                Duration.ofMillis(claimTtlMillis)
        );
        return Boolean.TRUE.equals(claimed);
    }

    @Override
    public boolean hasValidToken(String memberId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey(memberId)));
    }

    @Override
    public boolean hasActiveClaim(String memberId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(claimKey(memberId)));
    }

    @Override
    public boolean issueToken(String memberId, long nowMillis, long tokenTtlMillis) {
        Boolean issued = redisTemplate.opsForValue().setIfAbsent(
                tokenKey(memberId),
                String.valueOf(nowMillis + tokenTtlMillis),
                Duration.ofMillis(tokenTtlMillis)
        );
        if (!Boolean.TRUE.equals(issued)) {
            return false;
        }

        redisTemplate.opsForZSet().add(ACTIVE_TOKEN_KEY, memberId, nowMillis + tokenTtlMillis);
        return true;
    }

    @Override
    public long countActiveTokens(long nowMillis) {
        redisTemplate.opsForZSet().removeRangeByScore(ACTIVE_TOKEN_KEY, Double.NEGATIVE_INFINITY, nowMillis);
        Long count = redisTemplate.opsForZSet().zCard(ACTIVE_TOKEN_KEY);
        return count == null ? 0L : count;
    }

    @Override
    public void removeToken(String memberId) {
        redisTemplate.delete(tokenKey(memberId));
        redisTemplate.opsForZSet().remove(ACTIVE_TOKEN_KEY, memberId);
    }

    @Override
    public void clearClaim(String memberId) {
        redisTemplate.delete(claimKey(memberId));
    }

    String claimKey(String memberId) {
        return CLAIM_KEY_PREFIX + memberId;
    }

    String tokenKey(String memberId) {
        return TOKEN_KEY_PREFIX + memberId;
    }
}

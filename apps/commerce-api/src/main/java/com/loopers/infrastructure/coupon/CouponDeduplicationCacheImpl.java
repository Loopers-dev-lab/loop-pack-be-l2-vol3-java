package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponDeduplicationCache;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 기반 쿠폰 발급 중복 방지 캐시 구현.
 */
@Component
@RequiredArgsConstructor
public class CouponDeduplicationCacheImpl implements CouponDeduplicationCache {

    private static final String KEY_PREFIX = "coupon:issue:dedup:";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean trySetIfAbsent(Long userId, Long couponId, Duration ttl) {
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(buildKey(userId, couponId), "1", ttl);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void delete(Long userId, Long couponId) {
        stringRedisTemplate.delete(buildKey(userId, couponId));
    }

    private String buildKey(Long userId, Long couponId) {
        return KEY_PREFIX + userId + ":" + couponId;
    }
}

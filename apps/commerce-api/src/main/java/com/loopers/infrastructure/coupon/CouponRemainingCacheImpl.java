package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponRemainingCache;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 쿠폰 잔여 수량 캐시 구현.
 */
@Component
@RequiredArgsConstructor
public class CouponRemainingCacheImpl implements CouponRemainingCache {

    private static final String KEY_PREFIX = "coupon:";
    private static final String KEY_SUFFIX = ":remaining";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public String getRemaining(Long couponId) {
        return stringRedisTemplate.opsForValue().get(buildKey(couponId));
    }

    @Override
    public void setRemaining(Long couponId, String count) {
        stringRedisTemplate.opsForValue().set(buildKey(couponId), count);
    }

    @Override
    public Long decrementAndGet(Long couponId) {
        return stringRedisTemplate.opsForValue().decrement(buildKey(couponId));
    }

    @Override
    public void increment(Long couponId) {
        stringRedisTemplate.opsForValue().increment(buildKey(couponId));
    }

    private String buildKey(Long couponId) {
        return KEY_PREFIX + couponId + KEY_SUFFIX;
    }
}

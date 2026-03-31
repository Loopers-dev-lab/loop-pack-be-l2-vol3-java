package com.loopers.infrastructure.coupon;

import com.loopers.application.coupon.CouponIssueCountManager;
import com.loopers.config.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisCouponIssueCountManager implements CouponIssueCountManager {

    private static final String KEY_PREFIX = "coupon-promotion:issued-count:";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisCouponIssueCountManager(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 발급 카운트를 1 증가시키고 증가 후 값을 반환한다.
     * Redis INCR은 atomic하므로 동시 요청에도 정확한 순서가 보장된다.
     */
    @Override
    public long increment(Long couponId) {
        Long count = redisTemplate.opsForValue().increment(key(couponId));
        if (count == null) {
            throw new IllegalStateException("Redis INCR 실패: couponId=" + couponId);
        }
        return count;
    }

    /**
     * 컷오프 실패(거절) 시 또는 발급 실패 시 카운트를 되돌린다.
     */
    @Override
    public void decrement(Long couponId) {
        redisTemplate.opsForValue().decrement(key(couponId));
    }

    private String key(Long couponId) {
        return KEY_PREFIX + couponId;
    }
}

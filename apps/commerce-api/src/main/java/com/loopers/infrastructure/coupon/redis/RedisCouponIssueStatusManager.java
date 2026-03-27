package com.loopers.infrastructure.coupon.redis;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.coupon.CouponIssueStatusManager;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 기반 쿠폰 발급 상태 관리 구현체.
 *
 * <p>{@code coupon-issue-status:{couponId}:{userId}} 키에 상태값을 저장하며,
 * 24시간 TTL로 자동 만료된다.</p>
 */
@Slf4j
@Component
public class RedisCouponIssueStatusManager implements CouponIssueStatusManager {

    private static final String KEY_FORMAT = "coupon-issue-status:%d:%d";
    private static final Duration TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisCouponIssueStatusManager(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void markPending(Long couponId, Long userId) {
        String key = String.format(KEY_FORMAT, couponId, userId);
        redisTemplate.opsForValue().set(key, "PENDING", TTL);
    }

    @Override
    public Optional<String> getStatus(Long couponId, Long userId) {
        String key = String.format(KEY_FORMAT, couponId, userId);
        String status = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(status);
    }
}

package com.loopers.domain.coupon.service;

import com.loopers.domain.coupon.model.FirstComeCoupon;
import com.loopers.domain.coupon.repository.CouponIssueRequestRepository;
import com.loopers.domain.coupon.repository.FirstComeCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
@Component
public class FirstComeCouponService {

    private final FirstComeCouponRepository firstComeCouponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private final ConcurrentHashMap<Long, FirstComeCoupon> cache = new ConcurrentHashMap<>();

    private static final String REDIS_KEY_PREFIX = "coupon:fcfs:";

    private static final RedisScript<Long> FCFS_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local memberId = ARGV[1]
            local maxQty = tonumber(ARGV[2])
            local score = tonumber(ARGV[3])
            if redis.call('zscore', key, memberId) then
                return -1
            end
            if redis.call('zcard', key) >= maxQty then
                return -2
            end
            redis.call('zadd', key, score, memberId)
            return 1
            """, Long.class);

    public FirstComeCoupon getByTemplateId(Long templateId) {
        return cache.computeIfAbsent(templateId,
                id -> firstComeCouponRepository.findByTemplateId(id)
                        .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "선착순 쿠폰 이벤트가 없습니다.")));
    }

    @CircuitBreaker(name = "redis-fcfs", fallbackMethod = "fallbackAddToQueue")
    public void addToQueue(FirstComeCoupon fcCoupon, Long memberId) {
        String key = REDIS_KEY_PREFIX + fcCoupon.getCouponTemplateId();

        if (!fcCoupon.isActive()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "선착순 쿠폰 이벤트 기간이 아닙니다.");
        }

        Long result = redisTemplate.execute(FCFS_SCRIPT,
                List.of(key),
                memberId.toString(),
                String.valueOf(fcCoupon.getMaxQuantity()),
                String.valueOf(System.currentTimeMillis()));

        if (result != null && result == -1) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급 요청한 쿠폰입니다.");
        }
        if (result != null && result == -2) {
            throw new CoreException(ErrorType.BAD_REQUEST, "선착순 쿠폰이 모두 소진되었습니다.");
        }
    }

    public void removeFromQueue(Long templateId, Long memberId) {
        String key = REDIS_KEY_PREFIX + templateId;
        redisTemplate.opsForZSet().remove(key, memberId.toString());
    }

    public void fallbackAddToQueue(FirstComeCoupon fcCoupon, Long memberId, Exception e) {
        log.warn("Redis 장애 발생, DB Fallback 처리 - templateId: {}", fcCoupon.getCouponTemplateId(), e);

        if (!fcCoupon.isActive()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "선착순 쿠폰 이벤트 기간이 아닙니다.");
        }

        if (couponIssueRequestRepository.existsByTemplateIdAndMemberId(fcCoupon.getCouponTemplateId(), memberId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급 요청한 쿠폰입니다.");
        }

        long count = couponIssueRequestRepository.countByTemplateId(fcCoupon.getCouponTemplateId());
        if (count >= fcCoupon.getMaxQuantity()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "선착순 쿠폰이 모두 소진되었습니다.");
        }
    }
}

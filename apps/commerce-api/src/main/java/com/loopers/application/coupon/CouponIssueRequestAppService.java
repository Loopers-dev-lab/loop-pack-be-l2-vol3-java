package com.loopers.application.coupon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CouponIssueRequestAppService {
    private final CouponIssueMessagePublisher messagePublisher;
    private final StringRedisTemplate redisTemplate;

    private static final String STATUS_KEY_PREFIX = "coupon:issue:status:";
    private static final Duration STATUS_TTL = Duration.ofMinutes(10);

    public String requestCouponIssue(Long couponId, Long userId) {
        String requestId = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, "PENDING", STATUS_TTL);

        CouponIssueMessage message = new CouponIssueMessage(requestId, couponId, userId);
        messagePublisher.publish(message);
        log.info("쿠폰 비동기 발급 요청: requestId={}, couponId={}, userId={}", requestId, couponId, userId);

        return requestId;
    }

    public Optional<String> getIssueRequestStatus(String requestId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + requestId));
    }
}

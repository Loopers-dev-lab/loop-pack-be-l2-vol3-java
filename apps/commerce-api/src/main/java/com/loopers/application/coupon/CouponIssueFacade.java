package com.loopers.application.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponIssueRequestMessage;
import com.loopers.domain.coupon.CouponIssueResultModel;
import com.loopers.domain.coupon.CouponIssueResultRepository;
import com.loopers.infrastructure.monitoring.EventMetrics;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 선착순 쿠폰 발급 Facade — Thin Producer 전략.
 *
 * <p>Producer에서 DB TX를 사용하지 않는다.
 * Redis SETNX(중복 거절) + Redis DECR(수량 게이트키퍼) + Kafka send만 수행.
 * 모든 DB 작업은 Consumer(commerce-streamer)에서 단일 TX로 원자적 처리.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CouponIssueFacade {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final CouponIssueResultRepository resultRepository;
    private final EventMetrics eventMetrics;

    public String requestRushIssue(Long userId, Long couponId) {
        String requestId = UUID.randomUUID().toString();

        // Phase 1: Redis SETNX — 중복 요청 거절
        if (!tryDeduplication(userId, couponId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급 요청한 쿠폰입니다");
        }

        // Phase 2: Redis DECR — 수량 소진 즉시 거절
        if (!tryDecrementRemaining(couponId)) {
            removeDeduplication(userId, couponId);
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰이 모두 소진되었습니다.");
        }

        // Phase 3: Kafka send — Consumer에게 처리 위임
        try {
            String jsonMessage = objectMapper.writeValueAsString(
                new CouponIssueRequestMessage(requestId, userId, couponId, LocalDateTime.now()));
            kafkaTemplate.send("coupon-issue-requests",
                couponId.toString(), jsonMessage).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            restoreRemaining(couponId);
            removeDeduplication(userId, couponId);
            throw new CoreException(ErrorType.INTERNAL_ERROR, "발급 요청 실패. 다시 시도해주세요.");
        }

        return requestId;
    }

    public CouponIssueResultModel getIssueResult(String requestId) {
        return resultRepository.findById(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 처리 중입니다"));
    }

    private boolean tryDeduplication(Long userId, Long couponId) {
        try {
            String key = "coupon:issue:dedup:" + userId + ":" + couponId;
            Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofHours(24));
            return Boolean.TRUE.equals(firstTime);
        } catch (Exception e) {
            log.warn("[Redis장애] 중복 확인 실패, Consumer에서 방어", e);
            return true;
        }
    }

    private void removeDeduplication(Long userId, Long couponId) {
        try {
            redisTemplate.delete("coupon:issue:dedup:" + userId + ":" + couponId);
        } catch (Exception e) {
            log.warn("[Redis장애] dedup key 삭제 실패, TTL(24h) 후 자동 만료", e);
        }
    }

    private boolean tryDecrementRemaining(Long couponId) {
        try {
            String key = "coupon:" + couponId + ":remaining";
            Long remaining = redisTemplate.opsForValue().decrement(key);
            if (remaining == null || remaining < 0) {
                restoreRemaining(couponId);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("[Redis장애] DECR 실패, DB CAS fallback", e);
            eventMetrics.incrementRedisFallback();
            return true;
        }
    }

    private void restoreRemaining(Long couponId) {
        try {
            redisTemplate.opsForValue().increment("coupon:" + couponId + ":remaining");
        } catch (Exception e) {
            log.warn("[Redis장애] INCR 복원 실패, 동기화 배치에서 보정", e);
            eventMetrics.incrementIncrRestoreFail();
        }
    }
}

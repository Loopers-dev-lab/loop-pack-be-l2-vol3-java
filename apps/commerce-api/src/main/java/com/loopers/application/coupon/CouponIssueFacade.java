package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestMessage;
import com.loopers.domain.coupon.CouponIssueResultModel;
import com.loopers.domain.coupon.CouponIssueResultRepository;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.infrastructure.monitoring.EventMetrics;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 선착순 쿠폰 발급 Facade (Thin Producer).
 * <p>
 * Redis SETNX(중복 방지) + DECR(잔여 수량 감소) + Kafka 발행으로 구성된다.
 * DB 트랜잭션을 사용하지 않아 빠른 응답을 보장한다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CouponIssueFacade {

    private static final String TOPIC = "coupon-issue-requests";
    private static final String REMAINING_KEY_PREFIX = "coupon:remaining:";
    private static final String ISSUED_USER_KEY_PREFIX = "coupon:issued:user:";

    private final CouponService couponService;
    private final CouponIssueResultRepository couponIssueResultRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final EventMetrics eventMetrics;

    /**
     * 선착순 쿠폰 발급을 요청한다.
     * <p>
     * 1. SETNX로 사용자 중복 요청 방지
     * 2. DECR로 잔여 수량 감소 (0 미만이면 INCR 복원 후 거부)
     * 3. Kafka로 발급 요청 메시지 발행
     * </p>
     *
     * @param userId   사용자 ID
     * @param couponId 쿠폰 ID
     * @return 요청 ID (requestId)
     * @throws CoreException 쿠폰 없음(COUPON_NOT_FOUND), 선착순 쿠폰 아님(BAD_REQUEST),
     *                       이미 발급(COUPON_ALREADY_ISSUED), 수량 소진(COUPON_NOT_AVAILABLE)
     */
    public String requestRushIssue(Long userId, Long couponId) {
        // 쿠폰 존재 및 선착순 쿠폰 여부 확인
        CouponModel coupon = couponService.findByIdForAdmin(couponId);
        if (!coupon.isRushCoupon()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "선착순 쿠폰이 아닙니다.");
        }

        // 1. SETNX로 중복 요청 방지
        String userKey = ISSUED_USER_KEY_PREFIX + couponId + ":" + userId;
        Boolean isNew = stringRedisTemplate.opsForValue().setIfAbsent(userKey, "1");
        if (Boolean.FALSE.equals(isNew)) {
            throw new CoreException(ErrorType.COUPON_ALREADY_ISSUED);
        }

        // 2. DECR로 잔여 수량 감소
        String remainingKey = REMAINING_KEY_PREFIX + couponId;
        try {
            Long remaining = stringRedisTemplate.opsForValue().decrement(remainingKey);
            if (remaining == null || remaining < 0) {
                // 수량 소진 — INCR로 복원 후 SETNX 제거
                stringRedisTemplate.opsForValue().increment(remainingKey);
                stringRedisTemplate.delete(userKey);
                throw new CoreException(ErrorType.COUPON_NOT_AVAILABLE, "쿠폰 수량이 소진되었습니다.");
            }
        } catch (CoreException e) {
            throw e;
        } catch (Exception e) {
            // Redis 오류 시 SETNX 제거
            stringRedisTemplate.delete(userKey);
            eventMetrics.incrementRedisFallback();
            throw new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 발급 처리 중 오류가 발생했습니다.");
        }

        // 3. Kafka로 발급 요청 메시지 발행
        String requestId = UUID.randomUUID().toString();
        CouponIssueRequestMessage message = new CouponIssueRequestMessage(
                requestId, userId, couponId, LocalDateTime.now());
        try {
            String json = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(TOPIC, String.valueOf(couponId), json);
            log.info("선착순 쿠폰 발급 요청 발행: requestId={}, userId={}, couponId={}",
                    requestId, userId, couponId);
        } catch (Exception e) {
            // Kafka 발행 실패 시 Redis 복원
            try {
                stringRedisTemplate.opsForValue().increment(REMAINING_KEY_PREFIX + couponId);
            } catch (Exception restoreEx) {
                log.warn("[Redis장애] INCR 복원 실패, 동기화 배치에서 보정", restoreEx);
                eventMetrics.incrementIncrRestoreFail();
            }
            stringRedisTemplate.delete(userKey);
            throw new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 발급 요청 처리 중 오류가 발생했습니다.");
        }

        return requestId;
    }

    /**
     * 발급 요청 결과를 조회한다.
     *
     * @param requestId 요청 ID
     * @return 발급 결과 (없으면 empty — 아직 처리 중)
     */
    public Optional<CouponIssueResultModel> getIssueResult(String requestId) {
        return couponIssueResultRepository.findById(requestId);
    }
}

package com.loopers.interfaces.consumer;

import com.loopers.domain.coupon.CouponIssueRequestMessage;
import com.loopers.domain.coupon.CouponIssueResultModel;
import com.loopers.domain.coupon.CouponIssueResultRepository;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.idempotency.EventHandledModel;
import com.loopers.domain.idempotency.EventHandledRepository;
import com.loopers.infrastructure.monitoring.ConsumerMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선착순 쿠폰 발급 요청 처리기 (4-Layer Idempotent Processing).
 * <p>
 * 1. Layer 1 — 멱등 체크: requestId 해시 기반 event_handled 테이블 확인
 * 2. Layer 2 — 중복 발급 체크: userId + couponId + ISSUED 조합 확인
 * 3. Layer 3 — CAS 발급 카운트 증가: issued_count < max_quantity 조건부 UPDATE
 * 4. Layer 4 — UserCoupon 생성 + 결과 기록 + event_handled 기록
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CouponIssueProcessor {

    private static final String REMAINING_KEY_PREFIX = "coupon:remaining:";

    private final CouponIssueResultRepository couponIssueResultRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final EventHandledRepository eventHandledRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ConsumerMetrics consumerMetrics;

    /**
     * 쿠폰 발급 요청을 처리한다 (멱등).
     */
    @Transactional
    public void process(CouponIssueRequestMessage message) {
        String requestId = message.requestId();
        Long userId = message.userId();
        Long couponId = message.couponId();
        Long eventId = (long) requestId.hashCode();

        // Layer 1. 멱등 체크 — 이미 처리된 이벤트이면 스킵
        if (eventHandledRepository.existsById(eventId)) {
            log.info("이미 처리된 요청: requestId={}", requestId);
            return;
        }

        // Layer 2. 중복 발급 체크 — 이미 ISSUED 상태인 발급 이력이 있으면 거부
        if (couponIssueResultRepository.existsByUserIdAndCouponIdAndStatus(
                userId, couponId, CouponIssueStatus.ISSUED)) {
            saveRejected(requestId, userId, couponId, "중복 발급");
            return;
        }

        // Layer 3. CAS 발급 카운트 증가
        int updated = couponRepository.incrementIssuedCountWithCas(couponId);
        if (updated == 0) {
            saveRejected(requestId, userId, couponId, "수량 소진");
            // Redis 잔여 수량 복원 (DECR 보상)
            stringRedisTemplate.opsForValue().increment(REMAINING_KEY_PREFIX + couponId);
            return;
        }

        // Layer 4. UserCoupon 생성 + 발급 성공 결과 기록 + 멱등 기록
        UserCouponModel userCoupon = UserCouponModel.create(userId, couponId);
        userCouponRepository.save(userCoupon);

        CouponIssueResultModel result = CouponIssueResultModel.issued(requestId, userId, couponId);
        couponIssueResultRepository.save(result);

        eventHandledRepository.save(new EventHandledModel(eventId));

        log.info("쿠폰 발급 성공: requestId={}, userId={}, couponId={}", requestId, userId, couponId);
    }

    private void saveRejected(String requestId, Long userId, Long couponId, String reason) {
        CouponIssueResultModel result = CouponIssueResultModel.rejected(requestId, userId, couponId, reason);
        couponIssueResultRepository.save(result);
        log.info("쿠폰 발급 거부: requestId={}, userId={}, couponId={}, reason={}",
                requestId, userId, couponId, reason);
    }
}

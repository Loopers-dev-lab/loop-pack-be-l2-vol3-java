package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueResultModel;
import com.loopers.domain.coupon.CouponIssueResultService;
import com.loopers.domain.coupon.event.CouponIssueMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
public class CouponIssueFacade {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final CouponIssueResultService couponIssueResultService;

    @Transactional
    public String requestIssue(Long couponId, Long memberId) {
        String requestId = UUID.randomUUID().toString();

        // 1. 결과 테이블에 PENDING 상태로 저장
        couponIssueResultService.createPending(requestId, couponId, memberId);

        // 2. Kafka에 발급 요청 발행 (key = couponId → 같은 쿠폰은 같은 파티션)
        // .get()으로 동기 확인 — 전송 실패 시 예외 발생 → TX 롤백 → PENDING 레코드도 롤백
        try {
            CouponIssueMessage message = new CouponIssueMessage(
                    requestId, couponId, memberId, LocalDateTime.now());
            kafkaTemplate.send("coupon-issue-requests", String.valueOf(couponId), message)
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("쿠폰 발급 요청 Kafka 전송 실패", e);
        }

        return requestId;
    }

    public CouponIssueResultModel getIssueResult(String requestId) {
        return couponIssueResultService.getByRequestId(requestId);
    }
}

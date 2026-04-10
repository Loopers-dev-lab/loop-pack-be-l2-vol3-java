package com.loopers.application.coupon;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.outbox.OutboxEventPublisher;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;
    private final UserService userService;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public CouponIssueInfo requestIssue(String loginId, String rawPassword, Long couponId) {
        User user = userService.authenticate(loginId, rawPassword);
        CouponIssueRequest request = couponService.createRequest(couponId, user.getId());

        // Outbox에 저장 (같은 트랜잭션) → 릴레이가 coupon-issue-requests 토픽으로 발행
        outboxEventPublisher.publish(
            KafkaTopics.COUPON_ISSUE_REQUESTS,
            couponId.toString(),  // key=couponId → 같은 쿠폰은 같은 파티션 → 순서 보장
            "COUPON_ISSUE_REQUESTED",
            Map.of("requestId", request.getId(), "couponId", couponId, "userId", user.getId())
        );

        return CouponIssueInfo.from(request);
    }

    @Transactional(readOnly = true)
    public CouponIssueInfo getIssueResult(String loginId, String rawPassword, Long requestId) {
        userService.authenticate(loginId, rawPassword);
        CouponIssueRequest request = couponService.getRequest(requestId);
        return CouponIssueInfo.from(request);
    }
}

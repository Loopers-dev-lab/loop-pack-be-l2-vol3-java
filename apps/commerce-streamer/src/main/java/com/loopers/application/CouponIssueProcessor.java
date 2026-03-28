package com.loopers.application;

import com.loopers.domain.coupon.CouponIssueResultService;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.coupon.event.CouponIssueMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueProcessor {

    private final CouponService couponService;
    private final UserCouponService userCouponService;
    private final CouponIssueResultService couponIssueResultService;

    @Transactional
    public void process(CouponIssueMessage message) {
        // 1. 중복 발급 확인
        if (userCouponService.existsByCouponIdAndMemberId(
                message.couponId(), message.memberId())) {
            couponIssueResultService.markFailed(
                    message.requestId(), "이미 발급된 쿠폰입니다");
            return;
        }

        // 2. 수량 확인 + 차감 (비관적 락)
        CouponModel coupon = couponService.getByIdWithLock(message.couponId());
        if (coupon.getRemainingQuantity() <= 0) {
            couponIssueResultService.markFailed(
                    message.requestId(), "쿠폰이 모두 소진되었습니다");
            return;
        }
        coupon.increaseIssuedQuantity();

        // 3. 발급
        userCouponService.issue(message.couponId(), message.memberId());

        // 4. 결과 업데이트
        couponIssueResultService.markSuccess(message.requestId());

        log.info("쿠폰 발급 완료: requestId={}, couponId={}, memberId={}",
                message.requestId(), message.couponId(), message.memberId());
    }
}

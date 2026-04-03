package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.event.CouponIssueRequestedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 선착순 쿠폰 발급 요청.
     * 즉시 발급하지 않고 Kafka로 비동기 처리 위임.
     * 기본 유효성(만료, 수량)만 검증 후 이벤트 발행.
     */
    @Transactional
    public void requestCouponIssue(Long couponId, Long userId) {
        CouponModel coupon = couponService.getCoupon(couponId);

        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        if (!coupon.isQuantityAvailable()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 수량이 초과되었습니다.");
        }

        eventPublisher.publishEvent(new CouponIssueRequestedEvent(couponId, userId));
    }
}

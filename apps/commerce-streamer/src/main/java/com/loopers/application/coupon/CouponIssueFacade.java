package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.infrastructure.eventhandled.EventHandled;
import com.loopers.infrastructure.eventhandled.EventHandledJpaRepository;
import com.loopers.interfaces.consumer.payload.CouponIssuePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CouponIssueFacade {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Transactional
    public void processIssue(CouponIssuePayload payload) {
        if (eventHandledJpaRepository.existsById(payload.eventId())) {
            return;
        }

        Coupon coupon = couponRepository.findById(payload.couponId()).orElseThrow();
        CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(payload.requestId()).orElseThrow();

        if (coupon.isLimited()) {
            long issuedCount = userCouponRepository.countByCouponTemplateId(payload.couponId());
            if (issuedCount >= coupon.totalQuantity()) {
                request.markFailed("수량 초과");
                eventHandledJpaRepository.save(EventHandled.of(payload.eventId()));
                return;
            }
        }

        if (userCouponRepository.existsByUserIdAndCouponTemplateId(payload.userId(), payload.couponId())) {
            request.markFailed("중복 발급");
            eventHandledJpaRepository.save(EventHandled.of(payload.eventId()));
            return;
        }

        userCouponRepository.save(UserCoupon.of(payload.userId(), coupon));
        request.markSuccess();
        eventHandledJpaRepository.save(EventHandled.of(payload.eventId()));
    }
}

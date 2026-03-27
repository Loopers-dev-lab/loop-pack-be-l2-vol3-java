package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueProcessor {

    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponRepository couponRepository;
    private final IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Transactional
    public void process(String eventId, Long couponId, Long userId) {
        CouponIssueRequest request = couponIssueRequestRepository.findByEventId(eventId)
                .orElse(null);
        if (request == null || !request.isPending()) {
            return;
        }

        // Layer 1: 중복 발급 체크 (UK 예외 대신 사전 조회)
        if (issuedCouponJpaRepository.existsByCouponIdAndUserId(couponId, userId)) {
            request.reject("이미 발급된 쿠폰입니다");
            couponIssueRequestRepository.save(request);
            return;
        }

        // Layer 2: Atomic UPDATE (수량 차감 + 만료/삭제 검증)
        int affected = couponRepository.issueIfAvailable(couponId);
        if (affected == 0) {
            request.reject("발급 가능 수량이 모두 소진되었습니다");
            couponIssueRequestRepository.save(request);
            return;
        }

        // Layer 3: 발급 레코드 생성
        issuedCouponJpaRepository.save(IssuedCoupon.create(couponId, userId));
        request.complete();
        couponIssueRequestRepository.save(request);
    }
}

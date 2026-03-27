package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueProcessor {

    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Transactional
    public void process(String eventId, Long couponId, Long userId) {
        CouponIssueRequest request = couponIssueRequestRepository.findByEventId(eventId)
                .orElse(null);
        if (request == null || !request.isPending()) {
            return;
        }

        int affected = couponIssueRequestRepository.issueIfAvailable(couponId);
        if (affected == 0) {
            request.reject("발급 가능 수량이 모두 소진되었습니다");
            couponIssueRequestRepository.save(request);
            return;
        }

        try {
            issuedCouponJpaRepository.save(IssuedCoupon.create(couponId, userId));
            request.complete();
        } catch (DataIntegrityViolationException e) {
            request.reject("이미 발급된 쿠폰입니다");
        }
        couponIssueRequestRepository.save(request);
    }
}

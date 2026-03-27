package com.loopers.application;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class CouponIssueProcessor {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;

    @Transactional
    public void process(Long requestId, Long couponId, Long userId) {
        CouponIssueRequest request = couponIssueRequestRepository.findById(requestId).orElse(null);
        if (request == null) {
            log.warn("[CouponIssueProcessor] 발급 요청을 찾을 수 없음, requestId={}", requestId);
            return;
        }

        if (!request.isPending()) {
            log.info("[CouponIssueProcessor] 이미 처리된 요청, requestId={}", requestId);
            return;
        }

        if (issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            request.fail("이미 발급된 쿠폰입니다.");
            return;
        }

        Coupon coupon = couponRepository.findIssuableCoupon(couponId).orElse(null);
        if (coupon == null) {
            request.fail("발급 가능한 쿠폰이 아닙니다.");
            return;
        }

        try {
            issuedCouponRepository.save(IssuedCoupon.create(userId, couponId, coupon.getExpiresAt()));
        } catch (DataIntegrityViolationException e) {
            log.warn("[CouponIssueProcessor] 중복 발급 감지, requestId={}, couponId={}, userId={}", requestId, couponId, userId);
            request.fail("이미 발급된 쿠폰입니다.");
            return;
        }

        request.succeed();
    }
}

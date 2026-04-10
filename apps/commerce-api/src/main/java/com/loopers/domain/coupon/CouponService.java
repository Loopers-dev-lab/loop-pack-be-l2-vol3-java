package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;

    @Transactional(readOnly = true)
    public Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
    }

    @Transactional
    public CouponIssueRequest createRequest(Long couponId, Long userId) {
        getCoupon(couponId);
        return couponIssueRequestRepository.save(new CouponIssueRequest(couponId, userId));
    }

    @Transactional(readOnly = true)
    public CouponIssueRequest getRequest(Long requestId) {
        return couponIssueRequestRepository.findById(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다."));
    }
}

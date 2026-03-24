package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository jpaRepository;

    @Override
    public CouponIssueRequest save(CouponIssueRequest request) {
        return jpaRepository.save(request);
    }

    @Override
    public Optional<CouponIssueRequest> findByRequestIdAndUserId(String requestId, Long userId) {
        return jpaRepository.findByRequestIdAndUserId(requestId, userId);
    }

    @Override
    public Optional<CouponIssueRequest> findByCouponIdAndUserId(Long couponId, Long userId) {
        return jpaRepository.findByCouponIdAndUserId(couponId, userId);
    }

    @Override
    public Optional<CouponIssueRequest> findByRequestId(String requestId) {
        return jpaRepository.findByRequestId(requestId);
    }
}

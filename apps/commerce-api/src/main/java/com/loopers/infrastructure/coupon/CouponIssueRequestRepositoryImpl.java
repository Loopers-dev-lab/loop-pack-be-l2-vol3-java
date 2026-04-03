package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository jpaRepository;

    @Override
    public CouponIssueRequest save(CouponIssueRequest request) {
        return jpaRepository.save(request);
    }

    @Override
    public Optional<CouponIssueRequest> findByCouponIdAndUserId(Long couponId, Long userId) {
        return jpaRepository.findByCouponIdAndUserId(couponId, userId);
    }
}

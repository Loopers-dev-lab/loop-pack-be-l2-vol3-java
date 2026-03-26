package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponIssueResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponIssueResultRepositoryImpl implements CouponIssueResultRepository {

    private final CouponIssueResultJpaRepository couponIssueResultJpaRepository;

    @Override
    public CouponIssueResult save(CouponIssueResult result) {
        return couponIssueResultJpaRepository.save(result);
    }

    @Override
    public Optional<CouponIssueResult> findByUserIdAndCouponId(Long userId, Long couponId) {
        return couponIssueResultJpaRepository.findByUserIdAndCouponId(userId, couponId);
    }
}

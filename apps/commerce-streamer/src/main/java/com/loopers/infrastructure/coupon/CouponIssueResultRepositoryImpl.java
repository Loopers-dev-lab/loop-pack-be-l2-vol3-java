package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueResultModel;
import com.loopers.domain.coupon.CouponIssueResultRepository;
import com.loopers.domain.coupon.CouponIssueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 쿠폰 발급 결과 레포지토리 구현체.
 */
@Repository
@RequiredArgsConstructor
public class CouponIssueResultRepositoryImpl implements CouponIssueResultRepository {

    private final CouponIssueResultJpaRepository couponIssueResultJpaRepository;

    @Override
    public CouponIssueResultModel save(CouponIssueResultModel model) {
        return couponIssueResultJpaRepository.save(model);
    }

    @Override
    public Optional<CouponIssueResultModel> findById(String requestId) {
        return couponIssueResultJpaRepository.findById(requestId);
    }

    @Override
    public boolean existsByUserIdAndCouponIdAndStatus(Long userId, Long couponId, CouponIssueStatus status) {
        return couponIssueResultJpaRepository.existsByUserIdAndCouponIdAndStatus(userId, couponId, status);
    }
}

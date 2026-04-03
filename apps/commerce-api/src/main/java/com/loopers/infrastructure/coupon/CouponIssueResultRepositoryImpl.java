package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponIssueResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class CouponIssueResultRepositoryImpl implements CouponIssueResultRepository {

    private final CouponIssueResultJpaRepository couponIssueResultJpaRepository;

    @Override
    public CouponIssueResult save(CouponIssueResult result) {
        return couponIssueResultJpaRepository.save(result);
    }

    @Override
    public Optional<CouponIssueResult> findById(Long id) {
        return couponIssueResultJpaRepository.findById(id);
    }

    @Override
    public Optional<CouponIssueResult> findByIdAndUserId(Long id, Long userId) {
        return couponIssueResultJpaRepository.findByIdAndUserId(id, userId);
    }
}

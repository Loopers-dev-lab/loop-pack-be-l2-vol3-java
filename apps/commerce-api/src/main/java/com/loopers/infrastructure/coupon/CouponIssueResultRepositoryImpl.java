package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueResultModel;
import com.loopers.domain.coupon.CouponIssueResultRepository;
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
    public Optional<CouponIssueResultModel> findById(String requestId) {
        return couponIssueResultJpaRepository.findById(requestId);
    }
}

package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository jpaRepository;

    public CouponIssueRequestRepositoryImpl(CouponIssueRequestJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CouponIssueRequestModel save(CouponIssueRequestModel model) {
        return jpaRepository.save(model);
    }

    @Override
    public Optional<CouponIssueRequestModel> findByRequestId(String requestId) {
        return jpaRepository.findById(requestId);
    }

    @Override
    public Optional<CouponIssueRequestModel> findByRequestIdAndUserId(String requestId, Long userId) {
        return jpaRepository.findByRequestIdAndUserId(requestId, userId);
    }
}

package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Override
    public Optional<CouponIssueRequestModel> findByRequestId(String requestId) {
        return couponIssueRequestJpaRepository.findByRequestId(requestId);
    }

    @Override
    public CouponIssueRequestModel save(CouponIssueRequestModel model) {
        return couponIssueRequestJpaRepository.save(model);
    }

    @Override
    public void delete(CouponIssueRequestModel model) {
        couponIssueRequestJpaRepository.delete(model);
    }
}

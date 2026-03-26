package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository jpaRepository;

    @Override
    public Long save(CouponIssueRequest request) {
        return jpaRepository.save(request).getId();
    }

    @Override
    public Optional<CouponIssueRequest> findByRequestId(String requestId) {
        return jpaRepository.findByRequestId(requestId);
    }
}

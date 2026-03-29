package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponIssueRequestRepositoryAdapter implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository jpaRepository;

    @Override
    public CouponIssueRequest save(CouponIssueRequest request) {
        return jpaRepository.save(request);
    }

    @Override
    public Optional<CouponIssueRequest> findById(Long id) {
        return jpaRepository.findById(id);
    }
}

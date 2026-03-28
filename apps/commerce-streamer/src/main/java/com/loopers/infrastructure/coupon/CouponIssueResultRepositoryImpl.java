package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueResultModel;
import com.loopers.domain.coupon.CouponIssueResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponIssueResultRepositoryImpl implements CouponIssueResultRepository {

    private final CouponIssueResultJpaRepository jpaRepository;

    @Override
    public Optional<CouponIssueResultModel> findByRequestId(String requestId) {
        return jpaRepository.findByRequestId(requestId);
    }

    @Override
    public CouponIssueResultModel save(CouponIssueResultModel model) {
        return jpaRepository.save(model);
    }
}

package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueResultModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponIssueResultJpaRepository extends JpaRepository<CouponIssueResultModel, Long> {
    Optional<CouponIssueResultModel> findByRequestId(String requestId);
}

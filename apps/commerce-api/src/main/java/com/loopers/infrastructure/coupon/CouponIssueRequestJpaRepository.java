package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequest, Long> {
    java.util.Optional<CouponIssueRequest> findByIdAndUserId(Long id, Long userId);
}

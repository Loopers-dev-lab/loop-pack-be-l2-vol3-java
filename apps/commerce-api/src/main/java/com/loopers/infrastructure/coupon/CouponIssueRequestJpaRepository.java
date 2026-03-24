package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequest, Long> {

    Optional<CouponIssueRequest> findByRequestIdAndUserId(String requestId, Long userId);

    Optional<CouponIssueRequest> findByCouponIdAndUserId(Long couponId, Long userId);

    Optional<CouponIssueRequest> findByRequestId(String requestId);
}

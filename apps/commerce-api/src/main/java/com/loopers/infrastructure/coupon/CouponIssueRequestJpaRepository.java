package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequest, Long> {

    List<CouponIssueRequest> findByCouponId(Long couponId);

    long countByCouponIdAndStatus(Long couponId, CouponIssueStatus status);
}

package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequestModel, String> {

    Optional<CouponIssueRequestModel> findByRequestIdAndUserId(String requestId, Long userId);
}

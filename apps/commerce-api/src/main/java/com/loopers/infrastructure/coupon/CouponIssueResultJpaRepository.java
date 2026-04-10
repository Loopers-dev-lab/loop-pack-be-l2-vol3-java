package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueResultModel;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 쿠폰 발급 결과 JPA 레포지토리.
 */
public interface CouponIssueResultJpaRepository extends JpaRepository<CouponIssueResultModel, String> {
}

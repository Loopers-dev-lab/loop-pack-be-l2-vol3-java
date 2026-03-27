package com.loopers.infrastructure.coupon.repository;

import com.loopers.infrastructure.coupon.entity.CouponIssueRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequestEntity, Long> {

    long countByCouponTemplateId(Long couponTemplateId);

    boolean existsByCouponTemplateIdAndMemberId(Long couponTemplateId, Long memberId);
}

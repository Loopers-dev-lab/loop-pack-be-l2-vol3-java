package com.loopers.infrastructure.coupon.repository;

import com.loopers.infrastructure.coupon.entity.CouponIssueRequestStreamerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponIssueRequestStreamerJpaRepository extends JpaRepository<CouponIssueRequestStreamerEntity, Long> {
}

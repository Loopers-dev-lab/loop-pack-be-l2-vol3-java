package com.loopers.infrastructure.event;

import com.loopers.domain.event.CouponIssueRequestModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequestModel, Long> {
}

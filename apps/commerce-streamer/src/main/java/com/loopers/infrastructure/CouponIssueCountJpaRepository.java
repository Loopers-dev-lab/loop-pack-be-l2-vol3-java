package com.loopers.infrastructure;

import com.loopers.domain.CouponIssueCount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponIssueCountJpaRepository extends JpaRepository<CouponIssueCount, Long> {}

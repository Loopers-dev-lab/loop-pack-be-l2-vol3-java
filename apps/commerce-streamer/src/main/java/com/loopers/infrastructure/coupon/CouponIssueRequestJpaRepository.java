package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequestEntity, Long> {

    @Modifying
    @Query("UPDATE CouponIssueRequestEntity e SET e.status = :status, e.failReason = :failReason WHERE e.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") CouponIssueRequestStatus status, @Param("failReason") String failReason);
}

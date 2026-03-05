package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

public interface CouponIssueJpaRepository extends JpaRepository<CouponIssue, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CouponIssue ci SET ci.status = :usedStatus"
        + " WHERE ci.id = :id AND ci.status = :availableStatus AND ci.expiredAt > :now")
    int markAsUsed(@Param("id") Long id, @Param("now") ZonedDateTime now,
                   @Param("usedStatus") CouponIssueStatus usedStatus,
                   @Param("availableStatus") CouponIssueStatus availableStatus);

    List<CouponIssue> findAllByMemberId(Long memberId);
    List<CouponIssue> findAllByCouponId(Long couponId);
}

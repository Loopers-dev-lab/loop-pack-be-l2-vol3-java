package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {

    @Modifying
    @Query("UPDATE Coupon c SET c.issuedCount = c.issuedCount + 1 " +
            "WHERE c.id = :couponId AND c.issuedCount < c.maxIssueCount " +
            "AND c.expiredAt > CURRENT_TIMESTAMP AND c.deletedAt IS NULL")
    int issueIfAvailable(@Param("couponId") Long couponId);
}

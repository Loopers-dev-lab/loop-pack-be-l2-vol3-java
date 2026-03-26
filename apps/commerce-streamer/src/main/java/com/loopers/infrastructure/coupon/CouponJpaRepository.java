package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 쿠폰 JPA 레포지토리 (Streamer 모듈).
 */
public interface CouponJpaRepository extends JpaRepository<CouponModel, Long> {

    @Modifying
    @Query("UPDATE CouponModel c SET c.issuedCount = c.issuedCount + 1 "
            + "WHERE c.couponId = :couponId AND c.issuedCount < c.maxQuantity")
    int incrementIssuedCountWithCas(@Param("couponId") Long couponId);
}

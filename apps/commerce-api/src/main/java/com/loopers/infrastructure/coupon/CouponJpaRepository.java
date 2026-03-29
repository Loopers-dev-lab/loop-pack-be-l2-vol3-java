package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {

    @Query("SELECT c FROM Coupon c WHERE c.id IN :ids AND c.deletedAt IS NULL")
    List<Coupon> findAllByIdIn(@Param("ids") List<Long> ids);

    @Modifying
    @Query("UPDATE Coupon c SET c.issuedQuantity = c.issuedQuantity + 1 " +
        "WHERE c.id = :couponId AND c.issuedQuantity < :maxQuantity")
    int incrementIssuedQuantity(@Param("couponId") Long couponId, @Param("maxQuantity") int maxQuantity);
}

package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCoupon, Long> {

    // Command

    @Modifying
    @Query("UPDATE IssuedCoupon ic SET ic.usedAt = CURRENT_TIMESTAMP " +
           "WHERE ic.id = :id AND ic.userId = :userId " +
           "AND ic.usedAt IS NULL")
    int markUsedIfAvailable(@Param("id") Long id, @Param("userId") Long userId);

    // Query

    @Query(value = "SELECT ic FROM IssuedCoupon ic WHERE ic.couponId = :couponId ORDER BY ic.createdAt DESC",
           countQuery = "SELECT COUNT(ic) FROM IssuedCoupon ic WHERE ic.couponId = :couponId")
    Page<IssuedCoupon> findAllByCouponId(Long couponId, Pageable pageable);

    @Query(value = "SELECT ic FROM IssuedCoupon ic WHERE ic.userId = :userId ORDER BY ic.createdAt DESC",
           countQuery = "SELECT COUNT(ic) FROM IssuedCoupon ic WHERE ic.userId = :userId")
    Page<IssuedCoupon> findAllByUserId(Long userId, Pageable pageable);

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);
}

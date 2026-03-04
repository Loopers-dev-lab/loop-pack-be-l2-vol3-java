package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCoupon, Long> {

    // Query

    List<IssuedCoupon> findAllByCouponId(Long couponId);

    @Query(value = "SELECT ic FROM IssuedCoupon ic WHERE ic.couponId = :couponId ORDER BY ic.createdAt DESC",
           countQuery = "SELECT COUNT(ic) FROM IssuedCoupon ic WHERE ic.couponId = :couponId")
    Page<IssuedCoupon> findAllByCouponId(Long couponId, Pageable pageable);

    @Query(value = "SELECT ic FROM IssuedCoupon ic WHERE ic.userId = :userId AND ic.deletedAt IS NULL ORDER BY ic.createdAt DESC",
           countQuery = "SELECT COUNT(ic) FROM IssuedCoupon ic WHERE ic.userId = :userId AND ic.deletedAt IS NULL")
    Page<IssuedCoupon> findActiveByUserId(Long userId, Pageable pageable);

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);
}

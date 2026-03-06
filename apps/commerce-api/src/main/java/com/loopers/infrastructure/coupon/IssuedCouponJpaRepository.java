package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCoupon, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT ic FROM IssuedCoupon ic WHERE ic.id = :id")
    Optional<IssuedCoupon> findByIdWithLock(@Param("id") Long id);

    Optional<IssuedCoupon> findByCouponIdAndUserId(Long couponId, Long userId);
    List<IssuedCoupon> findByUserId(Long userId);
    List<IssuedCoupon> findByCouponId(Long couponId);
    Page<IssuedCoupon> findByCouponId(Long couponId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT ic FROM IssuedCoupon ic WHERE ic.couponId = :couponId AND ic.userId = :userId")
    Optional<IssuedCoupon> findByCouponIdAndUserIdWithLock(@Param("couponId") Long couponId, @Param("userId") Long userId);
}

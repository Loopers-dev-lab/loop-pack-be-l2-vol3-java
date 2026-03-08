package com.loopers.infrastructure.coupon.persistence;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.loopers.domain.coupon.OwnedCoupon;

public interface OwnedCouponJpaRepository extends JpaRepository<OwnedCoupon, Long> {

    @Query("SELECT oc FROM OwnedCoupon oc JOIN FETCH oc.coupon WHERE oc.id = :id")
    Optional<OwnedCoupon> findByIdWithCoupon(Long id);

    @Query("SELECT oc FROM OwnedCoupon oc JOIN FETCH oc.coupon WHERE oc.coupon.id = :couponId")
    Slice<OwnedCoupon> findAllByCouponId(@Param("couponId") Long couponId, Pageable pageable);

    @Query("SELECT oc FROM OwnedCoupon oc JOIN FETCH oc.coupon WHERE oc.userId = :userId")
    Slice<OwnedCoupon> findAllByUserId(Long userId, Pageable pageable);

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);
}

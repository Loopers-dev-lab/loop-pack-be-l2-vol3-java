package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.FcfsCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FcfsCouponJpaRepository extends JpaRepository<FcfsCoupon, Long> {

    Optional<FcfsCoupon> findByCouponIdAndDeletedAtIsNull(Long couponId);
}

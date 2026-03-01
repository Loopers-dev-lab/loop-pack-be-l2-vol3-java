package com.loopers.domain.coupon;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface CouponRepository {

    Coupon save(Coupon coupon);

    Optional<Coupon> findById(Long couponId);

    Optional<Coupon> findByIdAndDeletedAtIsNull(Long couponId);

    Slice<Coupon> findAllBy(Pageable pageable);
}

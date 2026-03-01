package com.loopers.domain.coupon;

import com.loopers.domain.PageResult;

import java.util.Optional;

public interface CouponRepository {

    Coupon save(Coupon coupon);

    Optional<Coupon> findById(Long id);

    PageResult<Coupon> findAll(int page, int size);
}

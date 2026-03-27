package com.loopers.domain.coupon.repository;

import com.loopers.domain.coupon.model.FirstComeCoupon;

import java.util.Optional;

public interface FirstComeCouponRepository {

    Optional<FirstComeCoupon> findByTemplateId(Long couponTemplateId);
}

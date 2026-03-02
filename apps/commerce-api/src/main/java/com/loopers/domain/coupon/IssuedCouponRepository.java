package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponRepository {
    IssuedCoupon save(IssuedCoupon issuedCoupon);
    Optional<IssuedCoupon> findById(Long id);
    long countByCouponTemplateId(Long couponTemplateId);
    long countByCouponTemplateIdAndUserId(Long couponTemplateId, Long userId);
    List<IssuedCoupon> findAllByUserId(Long userId);
}

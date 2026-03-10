package com.loopers.domain.coupon;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface IssuedCouponRepository {
    IssuedCoupon save(IssuedCoupon issuedCoupon);
    Optional<IssuedCoupon> findById(Long id);
    long countByCouponTemplateId(Long couponTemplateId);
    long countByCouponTemplateIdAndUserId(Long couponTemplateId, Long userId);
    List<IssuedCoupon> findAllByUserId(Long userId);
    List<IssuedCoupon> findAllByCouponTemplateId(Long couponTemplateId);
    int useAtomically(Long id, Long orderId, ZonedDateTime usedAt);
    int restoreAtomically(Long id);
}

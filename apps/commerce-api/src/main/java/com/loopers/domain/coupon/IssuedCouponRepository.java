package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

/**
 * IssuedCoupon Repository 인터페이스.
 */
public interface IssuedCouponRepository {

    IssuedCoupon save(IssuedCoupon coupon);

    Optional<IssuedCoupon> findById(Long id);

    Optional<IssuedCoupon> findByIdWithLock(Long id);

    Optional<IssuedCoupon> findByIdAndUserId(Long id, Long userId);

    List<IssuedCoupon> findAllByUserId(Long userId, int offset, int limit);

    boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId);

    long countByUserId(Long userId);

    List<IssuedCoupon> findAllByCouponTemplateId(Long couponTemplateId, int offset, int limit);

    long countByCouponTemplateId(Long couponTemplateId);
}

package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponRepository {

    IssuedCoupon save(IssuedCoupon issuedCoupon);

    Optional<IssuedCoupon> findById(Long id);

    Optional<IssuedCoupon> findByIdForUpdate(Long id);

    List<IssuedCoupon> findByMemberId(Long memberId);

    boolean existsByMemberIdAndCouponTemplateId(Long memberId, Long couponTemplateId);

    List<IssuedCoupon> findByCouponTemplateId(Long couponTemplateId, int page, int size);

    long countByCouponTemplateId(Long couponTemplateId);
}

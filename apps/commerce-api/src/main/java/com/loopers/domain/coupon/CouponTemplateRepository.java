package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CouponTemplateRepository {
    CouponTemplate save(CouponTemplate couponTemplate);
    Optional<CouponTemplate> findById(Long id);
    Optional<CouponTemplate> findByIdForUpdate(Long id);
    List<CouponTemplate> findAllByIdIn(Set<Long> ids);
    List<CouponTemplate> findAll(int page, int size);
    List<CouponTemplate> findAllIssuable();
    long count();
}

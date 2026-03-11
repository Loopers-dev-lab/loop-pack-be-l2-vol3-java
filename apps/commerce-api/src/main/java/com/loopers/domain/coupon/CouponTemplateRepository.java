package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface CouponTemplateRepository {

    CouponTemplate save(CouponTemplate template);

    Optional<CouponTemplate> findById(Long id);

    List<CouponTemplate> findAll(CouponSearchCondition condition);

    CouponTemplate update(CouponTemplate template);

    void delete(Long id);
}

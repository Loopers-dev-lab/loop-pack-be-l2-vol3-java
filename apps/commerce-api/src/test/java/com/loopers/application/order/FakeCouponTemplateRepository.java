package com.loopers.application.order;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class FakeCouponTemplateRepository implements CouponTemplateRepository {

    private final Map<Long, CouponTemplate> store = new HashMap<>();

    public void addWithId(Long id, CouponTemplate template) {
        store.put(id, template);
    }

    @Override
    public CouponTemplate save(CouponTemplate couponTemplate) {
        return couponTemplate;
    }

    @Override
    public Optional<CouponTemplate> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }
}

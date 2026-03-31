package com.loopers.domain.coupon;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryCouponPromotionRepository implements CouponPromotionRepository {

    private final Map<Long, CouponPromotion> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public CouponPromotion save(CouponPromotion promotion) {
        if (promotion.getId() == 0L) {
            try {
                var idField = promotion.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(promotion, idGenerator.getAndIncrement());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        store.put(promotion.getId(), promotion);
        return promotion;
    }

    @Override
    public Optional<CouponPromotion> findByCouponId(Long couponId) {
        return store.values().stream()
                .filter(p -> p.getCouponId().equals(couponId))
                .findFirst();
    }
}

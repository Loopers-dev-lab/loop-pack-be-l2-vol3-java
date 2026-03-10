package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryCouponRepository implements CouponRepository {

    private final Map<Long, Coupon> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getId() == 0L) {
            try {
                var idField = coupon.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(coupon, idGenerator.getAndIncrement());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        store.put(coupon.getId(), coupon);
        return coupon;
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Page<Coupon> findAllByDeletedAtIsNull(Pageable pageable) {
        List<Coupon> result = store.values().stream()
            .filter(c -> c.getDeletedAt() == null)
            .toList();
        return new PageImpl<>(result, pageable, result.size());
    }
}

package com.loopers.fake;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FakeCouponRepository implements CouponRepository {

    private final Map<Long, Coupon> store = new ConcurrentHashMap<>();
    private long sequence = 1L;

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getId() == null || coupon.getId() == 0L) {
            long id = sequence++;
            setBaseEntityId(coupon, id);
        }
        store.put(coupon.getId(), coupon);
        return coupon;
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return Optional.ofNullable(store.get(id))
            .filter(coupon -> coupon.getDeletedAt() == null);
    }

    @Override
    public List<Coupon> findAll() {
        return store.values().stream()
            .filter(coupon -> coupon.getDeletedAt() == null)
            .toList();
    }

    private void setBaseEntityId(Object entity, long id) {
        try {
            Field idField = BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

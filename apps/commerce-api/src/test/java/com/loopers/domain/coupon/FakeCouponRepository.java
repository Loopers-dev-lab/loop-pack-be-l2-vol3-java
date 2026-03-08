package com.loopers.domain.coupon;

import com.loopers.domain.PageResult;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

class FakeCouponRepository implements CouponRepository {

    private final List<Coupon> store = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getId() == null || coupon.getId() == 0L) {
            setId(coupon, idGenerator.getAndIncrement());
            store.add(coupon);
        }
        return coupon;
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return store.stream()
            .filter(c -> c.getId().equals(id))
            .filter(c -> c.getDeletedAt() == null)
            .findFirst();
    }

    @Override
    public PageResult<Coupon> findAll(int page, int size) {
        List<Coupon> filtered = store.stream()
            .filter(c -> c.getDeletedAt() == null)
            .toList();
        int total = filtered.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Coupon> paged = filtered.subList(fromIndex, toIndex);
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResult<>(paged, page, size, total, totalPages);
    }

    private void setId(Coupon coupon, long id) {
        try {
            Field idField = coupon.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(coupon, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set Coupon id", e);
        }
    }
}

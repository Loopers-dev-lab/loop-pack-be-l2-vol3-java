package com.loopers.domain.coupon;

import com.loopers.domain.PageResult;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

class FakeCouponIssueRepository implements CouponIssueRepository {

    private final List<CouponIssue> store = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public CouponIssue save(CouponIssue couponIssue) {
        if (couponIssue.getId() == null || couponIssue.getId() == 0L) {
            setId(couponIssue, idGenerator.getAndIncrement());
            store.add(couponIssue);
        }
        return couponIssue;
    }

    @Override
    public Optional<CouponIssue> findById(Long id) {
        return store.stream()
            .filter(ci -> ci.getId().equals(id))
            .filter(ci -> ci.getDeletedAt() == null)
            .findFirst();
    }

    @Override
    public boolean existsByCouponIdAndUserId(Long couponId, Long userId) {
        return store.stream()
            .filter(ci -> ci.getDeletedAt() == null)
            .anyMatch(ci -> ci.getCouponId().equals(couponId) && ci.getUserId().equals(userId));
    }

    @Override
    public List<CouponIssue> findAllByUserId(Long userId) {
        return store.stream()
            .filter(ci -> ci.getDeletedAt() == null)
            .filter(ci -> ci.getUserId().equals(userId))
            .toList();
    }

    @Override
    public PageResult<CouponIssue> findByCouponId(Long couponId, int page, int size) {
        List<CouponIssue> filtered = store.stream()
            .filter(ci -> ci.getDeletedAt() == null)
            .filter(ci -> ci.getCouponId().equals(couponId))
            .toList();
        int total = filtered.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<CouponIssue> paged = filtered.subList(fromIndex, toIndex);
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResult<>(paged, page, size, total, totalPages);
    }

    private void setId(CouponIssue couponIssue, long id) {
        try {
            Field idField = couponIssue.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(couponIssue, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set CouponIssue id", e);
        }
    }
}

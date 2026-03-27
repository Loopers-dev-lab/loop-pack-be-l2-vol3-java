package com.loopers.domain.coupon;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryCouponIssueRequestRepository implements CouponIssueRequestRepository {

    private final Map<Long, CouponIssueRequest> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public CouponIssueRequest save(CouponIssueRequest request) {
        if (request.getId() == 0L) {
            boolean duplicate = store.values().stream()
                .anyMatch(r -> r.getUserId().equals(request.getUserId())
                    && r.getCouponId().equals(request.getCouponId()));
            if (duplicate) {
                throw new org.springframework.dao.DataIntegrityViolationException(
                    "UNIQUE constraint violated: (user_id, coupon_id)");
            }

            try {
                var idField = request.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(request, idGenerator.getAndIncrement());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        store.put(request.getId(), request);
        return request;
    }

    @Override
    public Optional<CouponIssueRequest> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<CouponIssueRequest> findByIdAndUserId(Long id, Long userId) {
        return findById(id).filter(request -> request.getUserId().equals(userId));
    }
}

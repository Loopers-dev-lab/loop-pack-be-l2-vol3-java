package com.loopers.fake;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FakeCouponIssueRequestRepository implements CouponIssueRequestRepository {

    private final Map<Long, CouponIssueRequest> store = new ConcurrentHashMap<>();
    private long sequence = 1L;

    @Override
    public CouponIssueRequest save(CouponIssueRequest request) {
        if (request.getId() == null) {
            setId(request, sequence++);
        }
        store.put(request.getId(), request);
        return request;
    }

    @Override
    public Optional<CouponIssueRequest> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    private void setId(CouponIssueRequest request, long id) {
        try {
            Field idField = CouponIssueRequest.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(request, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

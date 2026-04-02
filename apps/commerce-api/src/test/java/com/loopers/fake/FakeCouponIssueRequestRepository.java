package com.loopers.fake;

import com.loopers.domain.fcfs.CouponIssueRequest;
import com.loopers.domain.fcfs.CouponIssueRequestRepository;
import com.loopers.domain.fcfs.CouponIssueRequestStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트용 Fake CouponIssueRequestRepository.
 */
public class FakeCouponIssueRequestRepository implements CouponIssueRequestRepository {

    private final Map<String, CouponIssueRequest> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    @Override
    public CouponIssueRequest save(CouponIssueRequest request) {
        Long id = request.getId() != null ? request.getId() : idGenerator.incrementAndGet();
        CouponIssueRequest saved = CouponIssueRequest.reconstitute(
            id,
            request.getRequestId(),
            request.getCouponId(),
            request.getUserId(),
            request.getStatus(),
            request.getFailureReason(),
            request.getCreatedAt(),
            request.getUpdatedAt()
        );
        store.put(request.getRequestId(), saved);
        return saved;
    }

    @Override
    public Optional<CouponIssueRequest> findByRequestId(String requestId) {
        return Optional.ofNullable(store.get(requestId));
    }

    @Override
    public void updateStatus(String requestId, CouponIssueRequestStatus status, String failureReason) {
        CouponIssueRequest request = store.get(requestId);
        if (request != null) {
            CouponIssueRequest updated = CouponIssueRequest.reconstitute(
                request.getId(),
                request.getRequestId(),
                request.getCouponId(),
                request.getUserId(),
                status,
                failureReason,
                request.getCreatedAt(),
                request.getUpdatedAt()
            );
            store.put(requestId, updated);
        }
    }

    @Override
    public long countByStatus(CouponIssueRequestStatus status) {
        return store.values().stream()
            .filter(r -> r.getStatus() == status)
            .count();
    }

    @Override
    public long countByCouponIdAndStatus(Long couponId, CouponIssueRequestStatus status) {
        return store.values().stream()
            .filter(r -> r.getCouponId().equals(couponId) && r.getStatus() == status)
            .count();
    }

    public void clear() {
        store.clear();
        idGenerator.set(0);
    }
}

package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryIssuedCouponRepository implements IssuedCouponRepository {

    private final Map<Long, IssuedCoupon> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public IssuedCoupon save(IssuedCoupon issuedCoupon) {
        boolean alreadyIssued = store.values().stream()
            .anyMatch(ic -> ic.getUserId().equals(issuedCoupon.getUserId())
                && ic.getCouponId().equals(issuedCoupon.getCouponId()));
        if (alreadyIssued) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
        }

        if (issuedCoupon.getId() == 0L) {
            try {
                var idField = issuedCoupon.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(issuedCoupon, idGenerator.getAndIncrement());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        store.put(issuedCoupon.getId(), issuedCoupon);
        return issuedCoupon;
    }

    @Override
    public Optional<IssuedCoupon> findByIdAndUserId(Long id, Long userId) {
        return store.values().stream()
            .filter(ic -> ic.getId() == id && ic.getUserId().equals(userId))
            .findFirst();
    }

    @Override
    public List<IssuedCoupon> findByUserId(Long userId) {
        return store.values().stream()
            .filter(ic -> ic.getUserId().equals(userId))
            .toList();
    }

    @Override
    public int useById(Long id, Long userId) {
        throw new UnsupportedOperationException("Atomic UPDATE는 DB에 의존하므로 통합테스트에서 커버합니다.");
    }
}

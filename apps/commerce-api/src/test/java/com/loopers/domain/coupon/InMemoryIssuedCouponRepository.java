package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        return store.values().stream()
            .anyMatch(ic -> ic.getUserId().equals(userId) && ic.getCouponId().equals(couponId));
    }

    @Override
    public int useById(Long id, Long userId) {
        throw new UnsupportedOperationException("Atomic UPDATE는 DB에 의존하므로 통합테스트에서 커버합니다.");
    }

    @Override
    public int restoreById(Long id, Long userId) {
        throw new UnsupportedOperationException("Atomic UPDATE는 DB에 의존하므로 통합테스트에서 커버합니다.");
    }

    @Override
    public Page<IssuedCoupon> findByCouponId(Long couponId, Pageable pageable) {
        List<IssuedCoupon> result = store.values().stream()
            .filter(ic -> ic.getCouponId().equals(couponId))
            .toList();
        return new PageImpl<>(result, pageable, result.size());
    }
}

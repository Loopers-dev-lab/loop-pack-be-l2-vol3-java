package com.loopers.fake;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트용 Fake IssuedCouponRepository.
 */
public class FakeIssuedCouponRepository implements IssuedCouponRepository {

    private final Map<Long, IssuedCoupon> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public IssuedCoupon save(IssuedCoupon coupon) {
        Long id = coupon.getId();
        if (id == null) {
            id = idGenerator.getAndIncrement();
            coupon = IssuedCoupon.reconstitute(
                id,
                coupon.getUserId(),
                coupon.getCouponTemplateId(),
                coupon.getStatus(),
                coupon.getUsedAt(),
                coupon.getIssuedAt(),
                coupon.getExpiredAt()
            );
        }
        store.put(id, coupon);
        return coupon;
    }

    @Override
    public Optional<IssuedCoupon> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<IssuedCoupon> findByIdWithLock(Long id) {
        return findById(id);
    }

    @Override
    public Optional<IssuedCoupon> findByIdAndUserId(Long id, Long userId) {
        return findById(id)
            .filter(c -> c.getUserId().equals(userId));
    }

    @Override
    public List<IssuedCoupon> findAllByUserId(Long userId, int offset, int limit) {
        return store.values().stream()
            .filter(c -> c.getUserId().equals(userId))
            .sorted(Comparator.comparing(IssuedCoupon::getIssuedAt).reversed())
            .skip(offset)
            .limit(limit)
            .toList();
    }

    @Override
    public boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId) {
        return store.values().stream()
            .anyMatch(c -> c.getUserId().equals(userId)
                && c.getCouponTemplateId().equals(couponTemplateId));
    }

    @Override
    public long countByUserId(Long userId) {
        return store.values().stream()
            .filter(c -> c.getUserId().equals(userId))
            .count();
    }

    @Override
    public List<IssuedCoupon> findAllByCouponTemplateId(Long couponTemplateId, int offset, int limit) {
        return store.values().stream()
            .filter(c -> c.getCouponTemplateId().equals(couponTemplateId))
            .sorted(Comparator.comparing(IssuedCoupon::getIssuedAt).reversed())
            .skip(offset)
            .limit(limit)
            .toList();
    }

    @Override
    public long countByCouponTemplateId(Long couponTemplateId) {
        return store.values().stream()
            .filter(c -> c.getCouponTemplateId().equals(couponTemplateId))
            .count();
    }

    public void clear() {
        store.clear();
        idGenerator.set(1);
    }

    public int size() {
        return store.size();
    }
}

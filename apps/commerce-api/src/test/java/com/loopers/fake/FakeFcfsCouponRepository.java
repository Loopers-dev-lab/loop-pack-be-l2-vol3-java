package com.loopers.fake;

import com.loopers.domain.fcfs.FcfsCoupon;
import com.loopers.domain.fcfs.FcfsCouponRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트용 Fake FcfsCouponRepository.
 */
public class FakeFcfsCouponRepository implements FcfsCouponRepository {

    private final Map<Long, FcfsCoupon> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    @Override
    public FcfsCoupon save(FcfsCoupon coupon) {
        Long id = coupon.getId() != null ? coupon.getId() : idGenerator.incrementAndGet();
        FcfsCoupon saved = FcfsCoupon.reconstitute(
            id,
            coupon.getName(),
            coupon.getTotalQuantity(),
            coupon.getIssuedCount(),
            coupon.getCreatedAt()
        );
        store.put(id, saved);
        return saved;
    }

    @Override
    public Optional<FcfsCoupon> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<FcfsCoupon> findByIdWithLock(Long id) {
        return findById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }

    @Override
    public void incrementIssuedCount(Long id) {
        FcfsCoupon coupon = store.get(id);
        if (coupon != null) {
            coupon.incrementIssuedCount();
            store.put(id, coupon);
        }
    }

    public void clear() {
        store.clear();
        idGenerator.set(0);
    }
}

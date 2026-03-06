package com.loopers.application.order;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class FakeUserCouponRepository implements UserCouponRepository {

    private final Map<Long, UserCoupon> store = new HashMap<>();
    private long sequence = 1L;

    public void addWithId(Long id, UserCoupon userCoupon) {
        store.put(id, userCoupon);
    }

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        return userCoupon;
    }

    @Override
    public Optional<UserCoupon> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<UserCoupon> findByIdWithLock(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return store.values().stream()
            .filter(uc -> uc.getUserId().equals(userId))
            .toList();
    }
}

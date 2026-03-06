package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {

    UserCoupon save(UserCoupon userCoupon);

    Optional<UserCoupon> findById(Long id);

    Optional<UserCoupon> findByIdWithLock(Long id);

    List<UserCoupon> findByUserId(Long userId);
}

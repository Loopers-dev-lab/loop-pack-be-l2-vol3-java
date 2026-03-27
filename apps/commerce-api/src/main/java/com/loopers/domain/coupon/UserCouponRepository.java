package com.loopers.domain.coupon;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserCouponRepository {

    UserCoupon save(UserCoupon userCoupon);

    Optional<UserCoupon> findById(Long id);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    long countByCouponId(Long couponId);

    Page<UserCoupon> findAllByCouponId(Long couponId, Pageable pageable);

    Page<UserCoupon> findAllByUserId(Long userId, Pageable pageable);
}

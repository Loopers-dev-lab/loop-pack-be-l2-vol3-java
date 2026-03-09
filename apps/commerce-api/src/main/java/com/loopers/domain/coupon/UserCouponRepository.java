package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCoupon save(UserCoupon userCoupon);
    Optional<UserCoupon> findById(Long id);
    Optional<UserCoupon> findByIdWithLock(Long id);
    List<UserCoupon> findAllByUserId(Long userId);
    Page<UserCoupon> findAllByCouponId(Long couponId, Pageable pageable);
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
}

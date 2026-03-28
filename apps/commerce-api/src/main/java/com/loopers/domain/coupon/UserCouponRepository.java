package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCouponModel save(UserCouponModel userCoupon);
    Optional<UserCouponModel> findById(Long id);
    List<UserCouponModel> findAllByMemberId(Long memberId);
    Page<UserCouponModel> findAllByCouponId(Long couponId, Pageable pageable);
    boolean existsByCouponIdAndMemberId(Long couponId, Long memberId);
}

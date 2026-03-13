package com.loopers.domain.coupon;

import com.loopers.support.page.PageQuery;
import com.loopers.support.page.PagedResult;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 발급 쿠폰 레포지토리 인터페이스 (도메인 레이어).
 */
public interface UserCouponRepository {

    UserCouponModel save(UserCouponModel userCoupon);

    Optional<UserCouponModel> findById(Long userCouponId);

    Optional<UserCouponModel> findByIdWithLock(Long userCouponId);

    Optional<UserCouponModel> findByUserIdAndCouponId(Long userId, Long couponId);

    List<UserCouponModel> findAllByUserId(Long userId);

    List<UserCouponModel> findAllByCouponId(Long couponId);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    PagedResult<UserCouponModel> findAllByCouponIdPaged(Long couponId, PageQuery query);

    Optional<UserCouponModel> findByOrderId(Long orderId);
}

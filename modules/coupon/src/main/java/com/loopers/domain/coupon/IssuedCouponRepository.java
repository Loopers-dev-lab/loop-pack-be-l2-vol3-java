package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * 발급 쿠폰 영속성 인터페이스.
 */
public interface IssuedCouponRepository {

    Optional<IssuedCouponModel> findById(Long id);

    /**
     * 비관적 락으로 조회. 주문 시 쿠폰 사용 처리에서 중복 사용 방지용.
     */
    Optional<IssuedCouponModel> findByIdForUpdate(Long id);

    Page<IssuedCouponModel> findByUserId(Long userId, Pageable pageable);

    /** 조회 전용 프로젝션. */
    Page<IssuedCouponProjection> findByUserIdAsProjection(Long userId, Pageable pageable);

    Page<IssuedCouponModel> findByCouponId(Long couponId, Pageable pageable);

    /** 조회 전용 프로젝션. */
    Page<IssuedCouponProjection> findByCouponIdAsProjection(Long couponId, Pageable pageable);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    IssuedCouponModel save(IssuedCouponModel issuedCoupon);
}

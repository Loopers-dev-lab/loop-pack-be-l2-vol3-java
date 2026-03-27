package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponPromotion;
import com.loopers.domain.coupon.CouponPromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class CouponPromotionRepositoryImpl implements CouponPromotionRepository {

    private final CouponPromotionJpaRepository couponPromotionJpaRepository;

    @Override
    public Optional<CouponPromotion> findByCouponId(Long couponId) {
        return couponPromotionJpaRepository.findByCouponId(couponId);
    }
}

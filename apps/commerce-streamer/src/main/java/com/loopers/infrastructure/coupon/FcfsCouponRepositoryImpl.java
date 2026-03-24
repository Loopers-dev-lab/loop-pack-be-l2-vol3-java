package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.FcfsCoupon;
import com.loopers.domain.coupon.FcfsCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class FcfsCouponRepositoryImpl implements FcfsCouponRepository {

    private final FcfsCouponJpaRepository jpaRepository;

    @Override
    public Optional<FcfsCoupon> findByCouponId(Long couponId) {
        return jpaRepository.findByCouponIdAndDeletedAtIsNull(couponId);
    }

    @Override
    public FcfsCoupon save(FcfsCoupon fcfsCoupon) {
        return jpaRepository.save(fcfsCoupon);
    }
}

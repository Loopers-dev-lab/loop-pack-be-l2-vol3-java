package com.loopers.infrastructure.persistence.jpa.fcfs;

import com.loopers.domain.fcfs.FcfsIssuedCoupon;
import com.loopers.domain.fcfs.FcfsIssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * FcfsIssuedCouponRepository 구현체.
 */
@Repository
@RequiredArgsConstructor
public class FcfsIssuedCouponRepositoryImpl implements FcfsIssuedCouponRepository {

    private final FcfsIssuedCouponJpaRepository jpaRepository;

    @Override
    public FcfsIssuedCoupon save(FcfsIssuedCoupon issuedCoupon) {
        FcfsIssuedCouponJpaEntity entity = FcfsIssuedCouponMapper.toJpaEntity(issuedCoupon);
        FcfsIssuedCouponJpaEntity saved = jpaRepository.save(entity);
        return FcfsIssuedCouponMapper.toDomain(saved);
    }

    @Override
    public boolean existsByCouponIdAndUserId(Long couponId, Long userId) {
        return jpaRepository.existsByCouponIdAndUserId(couponId, userId);
    }

    @Override
    public long countByCouponId(Long couponId) {
        return jpaRepository.countByCouponId(couponId);
    }
}

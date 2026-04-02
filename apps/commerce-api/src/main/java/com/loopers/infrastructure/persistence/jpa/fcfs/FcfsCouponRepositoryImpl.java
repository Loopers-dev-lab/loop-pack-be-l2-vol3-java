package com.loopers.infrastructure.persistence.jpa.fcfs;

import com.loopers.domain.fcfs.FcfsCoupon;
import com.loopers.domain.fcfs.FcfsCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * FcfsCouponRepository 구현체.
 */
@Repository
@RequiredArgsConstructor
public class FcfsCouponRepositoryImpl implements FcfsCouponRepository {

    private final FcfsCouponJpaRepository jpaRepository;

    @Override
    public FcfsCoupon save(FcfsCoupon coupon) {
        FcfsCouponJpaEntity entity = FcfsCouponMapper.toJpaEntity(coupon);
        FcfsCouponJpaEntity saved = jpaRepository.save(entity);
        return FcfsCouponMapper.toDomain(saved);
    }

    @Override
    public Optional<FcfsCoupon> findById(Long id) {
        return jpaRepository.findById(id)
            .map(FcfsCouponMapper::toDomain);
    }

    @Override
    public Optional<FcfsCoupon> findByIdWithLock(Long id) {
        return jpaRepository.findByIdWithLock(id)
            .map(FcfsCouponMapper::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public void incrementIssuedCount(Long id) {
        jpaRepository.incrementIssuedCount(id);
    }
}

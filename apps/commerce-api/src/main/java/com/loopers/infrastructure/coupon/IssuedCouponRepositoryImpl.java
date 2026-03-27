package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCouponModel;
import com.loopers.domain.coupon.IssuedCouponProjection;
import com.loopers.domain.coupon.IssuedCouponRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class IssuedCouponRepositoryImpl implements IssuedCouponRepository {

    private final IssuedCouponJpaRepository jpaRepository;

    public IssuedCouponRepositoryImpl(IssuedCouponJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<IssuedCouponModel> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<IssuedCouponModel> findByIdForUpdate(Long id) {
        return jpaRepository.findByIdForUpdate(id);
    }

    @Override
    public Page<IssuedCouponModel> findByUserId(Long userId, Pageable pageable) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public Page<IssuedCouponProjection> findByUserIdAsProjection(Long userId, Pageable pageable) {
        return jpaRepository.findProjectionByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public Page<IssuedCouponModel> findByCouponId(Long couponId, Pageable pageable) {
        return jpaRepository.findByCouponIdOrderByCreatedAtDesc(couponId, pageable);
    }

    @Override
    public Page<IssuedCouponProjection> findByCouponIdAsProjection(Long couponId, Pageable pageable) {
        return jpaRepository.findProjectionByCouponIdOrderByCreatedAtDesc(couponId, pageable);
    }

    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        return jpaRepository.existsByUserIdAndCouponId(userId, couponId);
    }

    @Override
    public IssuedCouponModel save(IssuedCouponModel issuedCoupon) {
        return jpaRepository.save(issuedCoupon);
    }
}

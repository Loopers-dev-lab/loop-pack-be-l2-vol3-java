package com.loopers.infrastructure.coupon.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@link CouponRepository}의 인프라스트럭처 구현체.
 *
 * <p>{@link CouponJpaRepository}에 위임하여 쿠폰 영속성을 처리한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    @Override
    public Coupon save(Coupon coupon) {
        return couponJpaRepository.save(coupon);
    }

    @Override
    public Optional<Coupon> findById(Long couponId) {
        return couponJpaRepository.findById(couponId);
    }

    @Override
    public Optional<Coupon> findByIdAndDeletedAtIsNull(Long couponId) {
        return couponJpaRepository.findByIdAndDeletedAtIsNull(couponId);
    }

    @Override
    public Slice<Coupon> findAllBy(Pageable pageable) {
        return couponJpaRepository.findAllBy(pageable);
    }

    @Override
    public List<Coupon> findAllActive() {
        return couponJpaRepository.findAllActive();
    }
}

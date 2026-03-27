package com.loopers.infrastructure.coupon.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import com.loopers.domain.coupon.OwnedCoupon;
import com.loopers.domain.coupon.OwnedCouponRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@link OwnedCouponRepository}의 인프라스트럭처 구현체.
 *
 * <p>{@link OwnedCouponJpaRepository}에 위임하여 보유 쿠폰 영속성을 처리한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class OwnedCouponRepositoryImpl implements OwnedCouponRepository {

    private final OwnedCouponJpaRepository ownedCouponJpaRepository;

    @Override
    public OwnedCoupon save(OwnedCoupon ownedCoupon) {
        return ownedCouponJpaRepository.save(ownedCoupon);
    }

    @Override
    public Optional<OwnedCoupon> findByIdWithCoupon(Long id) {
        return ownedCouponJpaRepository.findByIdWithCoupon(id);
    }

    @Override
    public Slice<OwnedCoupon> findAllByCouponId(Long couponId, Pageable pageable) {
        return ownedCouponJpaRepository.findAllByCouponId(couponId, pageable);
    }

    @Override
    public Slice<OwnedCoupon> findAllByUserId(Long userId, Pageable pageable) {
        return ownedCouponJpaRepository.findAllByUserId(userId, pageable);
    }

    @Override
    public long countByCouponId(Long couponId) {
        return ownedCouponJpaRepository.countByCouponId(couponId);
    }

    @Override
    public List<Long> findUserIdsByCouponId(Long couponId) {
        return ownedCouponJpaRepository.findUserIdsByCouponId(couponId);
    }
}

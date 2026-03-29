package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponRepositoryAdapter implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    @Override
    public Coupon save(Coupon coupon) {
        return couponJpaRepository.save(coupon);
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return couponJpaRepository.findById(id)
            .filter(c -> c.getDeletedAt() == null);
    }

    @Override
    public Page<Coupon> findAll(Pageable pageable) {
        return couponJpaRepository.findAll(pageable);
    }

    @Override
    public List<Coupon> findAllByIds(List<Long> ids) {
        return couponJpaRepository.findAllByIdIn(ids);
    }

    @Override
    public int incrementIssuedQuantity(Long couponId, int maxQuantity) {
        return couponJpaRepository.incrementIssuedQuantity(couponId, maxQuantity);
    }
}

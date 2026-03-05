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
public class CouponRepositoryImpl implements CouponRepository {
    private final CouponJpaRepository couponJpaRepository;

    @Override
    public Coupon save(Coupon coupon) {
        return couponJpaRepository.save(coupon);
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return couponJpaRepository.findByIdAndDeletedFalse(id);
    }

    @Override
    public Optional<Coupon> findByIdWithLock(Long id) {
        return couponJpaRepository.findByIdWithLock(id);
    }

    @Override
    public List<Coupon> findAll() {
        return couponJpaRepository.findByDeletedFalse();
    }

    @Override
    public List<Coupon> findByIdIn(List<Long> ids) {
        return couponJpaRepository.findByIdInAndDeletedFalse(ids);
    }

    @Override
    public Page<Coupon> findAll(Pageable pageable) {
        return couponJpaRepository.findByDeletedFalse(pageable);
    }
}

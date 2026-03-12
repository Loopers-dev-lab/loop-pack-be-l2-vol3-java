package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository couponJpaRepository;

    @Override
    public CouponModel save(CouponModel couponModel) {
        return couponJpaRepository.save(couponModel);
    }

    @Override
    public Optional<CouponModel> findById(Long id) {
        return couponJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Page<CouponModel> findAll(Pageable pageable) {
        return couponJpaRepository.findAllByDeletedAtIsNull(pageable);
    }
}

package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository jpaRepository;

    @Override
    public int issueIfAvailable(Long couponId) {
        return jpaRepository.issueIfAvailable(couponId);
    }
}

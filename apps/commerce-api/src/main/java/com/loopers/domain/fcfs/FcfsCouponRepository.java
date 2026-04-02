package com.loopers.domain.fcfs;

import java.util.Optional;

/**
 * 선착순 쿠폰 Repository 인터페이스.
 */
public interface FcfsCouponRepository {

    FcfsCoupon save(FcfsCoupon coupon);

    Optional<FcfsCoupon> findById(Long id);

    Optional<FcfsCoupon> findByIdWithLock(Long id);

    boolean existsById(Long id);

    void incrementIssuedCount(Long id);
}

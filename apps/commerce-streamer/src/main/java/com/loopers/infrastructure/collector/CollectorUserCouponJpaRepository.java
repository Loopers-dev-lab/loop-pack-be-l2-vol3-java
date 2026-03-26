package com.loopers.infrastructure.collector;

import com.loopers.domain.collector.CollectorUserCouponModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectorUserCouponJpaRepository extends JpaRepository<CollectorUserCouponModel, Long> {
    boolean existsByUserIdAndCoupon_Id(Long userId, Long couponId);

    long countByCoupon_Id(Long couponId);
}

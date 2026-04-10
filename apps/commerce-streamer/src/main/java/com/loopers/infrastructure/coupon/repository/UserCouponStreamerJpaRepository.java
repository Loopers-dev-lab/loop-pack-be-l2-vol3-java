package com.loopers.infrastructure.coupon.repository;

import com.loopers.infrastructure.coupon.entity.UserCouponStreamerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCouponStreamerJpaRepository extends JpaRepository<UserCouponStreamerEntity, Long> {
}

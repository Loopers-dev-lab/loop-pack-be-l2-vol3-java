package com.loopers.infrastructure.coupon.persistence;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.coupon.Coupon;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByIdAndDeletedAtIsNull(Long couponId);

    Slice<Coupon> findAllBy(Pageable pageable);
}

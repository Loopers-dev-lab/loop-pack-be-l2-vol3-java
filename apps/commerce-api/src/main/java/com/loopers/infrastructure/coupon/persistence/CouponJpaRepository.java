package com.loopers.infrastructure.coupon.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.coupon.Coupon;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {

    Slice<Coupon> findAllBy(Pageable pageable);
}

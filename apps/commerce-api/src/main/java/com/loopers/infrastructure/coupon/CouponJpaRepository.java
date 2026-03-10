package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {
    Page<Coupon> findAllByDeletedAtIsNull(Pageable pageable);
}

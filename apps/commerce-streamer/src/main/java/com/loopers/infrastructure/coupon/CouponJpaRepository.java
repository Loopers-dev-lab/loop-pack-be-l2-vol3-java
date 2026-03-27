package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByIdAndDeletedAtIsNullAndExpiresAtAfter(Long id, LocalDateTime now);
}

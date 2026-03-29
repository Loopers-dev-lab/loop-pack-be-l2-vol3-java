package com.loopers.infrastructure.coupon.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.loopers.domain.coupon.Coupon;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByIdAndDeletedAtIsNull(Long couponId);

    Slice<Coupon> findAllBy(Pageable pageable);

    @Query("SELECT c FROM Coupon c WHERE c.deletedAt IS NULL AND c.expiredAt > CURRENT_TIMESTAMP AND c.totalQuantity > 0")
    List<Coupon> findAllActive();
}

package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {

    // Query

    @Query(value = "SELECT c FROM Coupon c WHERE c.deletedAt IS NULL ORDER BY c.createdAt DESC",
           countQuery = "SELECT COUNT(c) FROM Coupon c WHERE c.deletedAt IS NULL")
    Page<Coupon> findAllActive(Pageable pageable);
}

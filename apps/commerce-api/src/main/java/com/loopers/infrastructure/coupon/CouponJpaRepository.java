package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {

    @Query("SELECT c FROM Coupon c WHERE c.id IN :ids AND c.deletedAt IS NULL")
    List<Coupon> findAllByIdIn(@Param("ids") List<Long> ids);
}

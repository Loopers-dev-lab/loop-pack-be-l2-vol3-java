package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<CouponModel, Long> {

    @Query("SELECT c FROM CouponModel c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<CouponModel> findByIdAndDeletedAtIsNull(Long id);

    @Query(
        value = "SELECT c FROM CouponModel c WHERE c.deletedAt IS NULL",
        countQuery = "SELECT COUNT(c) FROM CouponModel c WHERE c.deletedAt IS NULL"
    )
    Page<CouponModel> findAllByDeletedAtIsNull(Pageable pageable);
}

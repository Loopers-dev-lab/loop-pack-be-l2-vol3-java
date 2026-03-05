package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByIdAndDeletedFalse(Long id);
    List<Coupon> findByIdInAndDeletedFalse(List<Long> ids);
    List<Coupon> findByDeletedFalse();
    Page<Coupon> findByDeletedFalse(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id AND c.deleted = false")
    Optional<Coupon> findByIdWithLock(@Param("id") Long id);
}

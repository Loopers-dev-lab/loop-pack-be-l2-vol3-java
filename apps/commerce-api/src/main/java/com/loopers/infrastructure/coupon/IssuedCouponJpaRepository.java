package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCoupon, Long> {
    Optional<IssuedCoupon> findByIdAndUserId(Long id, Long userId);
    List<IssuedCoupon> findByUserId(Long userId);
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
    Page<IssuedCoupon> findByCouponId(Long couponId, Pageable pageable);

    @Modifying
    @Query("UPDATE IssuedCoupon ic SET ic.usedAt = :now WHERE ic.id = :id AND ic.userId = :userId AND ic.usedAt IS NULL")
    int useById(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE IssuedCoupon ic SET ic.usedAt = null WHERE ic.id = :id AND ic.userId = :userId AND ic.usedAt IS NOT NULL")
    int restoreById(@Param("id") Long id, @Param("userId") Long userId);
}

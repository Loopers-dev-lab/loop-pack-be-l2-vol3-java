package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCouponModel;
import com.loopers.domain.coupon.IssuedCouponProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCouponModel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM IssuedCouponModel i WHERE i.id = :id")
    Optional<IssuedCouponModel> findByIdForUpdate(@Param("id") Long id);

    Page<IssuedCouponModel> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<IssuedCouponProjection> findProjectionByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<IssuedCouponModel> findByCouponIdOrderByCreatedAtDesc(Long couponId, Pageable pageable);

    Page<IssuedCouponProjection> findProjectionByCouponIdOrderByCreatedAtDesc(Long couponId, Pageable pageable);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
}

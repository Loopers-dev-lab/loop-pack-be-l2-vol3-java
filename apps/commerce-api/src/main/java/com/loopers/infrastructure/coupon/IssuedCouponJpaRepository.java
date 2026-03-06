package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCoupon, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM IssuedCoupon c WHERE c.id = :id")
    Optional<IssuedCoupon> findByIdForUpdate(@Param("id") Long id);

    List<IssuedCoupon> findByMemberId(Long memberId);

    boolean existsByMemberIdAndCouponTemplateId(Long memberId, Long couponTemplateId);

    List<IssuedCoupon> findByCouponTemplateId(Long couponTemplateId, Pageable pageable);

    long countByCouponTemplateId(Long couponTemplateId);
}

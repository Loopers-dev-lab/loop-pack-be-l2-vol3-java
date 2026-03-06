package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssuedCouponRepository {
    IssuedCoupon save(IssuedCoupon issuedCoupon);

    Optional<IssuedCoupon> findByMemberIdAndCouponId(String memberId, UUID couponId);

    int markUsedAtomically(String memberId, UUID couponId, LocalDateTime now);

    int markAvailableAtomically(String memberId, UUID couponId, LocalDateTime now);

    Page<IssuedCoupon> findByCouponId(UUID couponId, Pageable pageable);

    List<IssuedCoupon> findByMemberId(String memberId);
}

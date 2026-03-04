package com.loopers.domain.coupon.repository;

import com.loopers.domain.coupon.model.UserCoupon;
import com.loopers.domain.coupon.model.UserCouponItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserCouponRepository {

    UserCoupon save(UserCoupon userCoupon);

    Optional<UserCoupon> findById(Long id);

    Optional<UserCoupon> findByIdWithLock(Long id);

    Page<UserCoupon> findByMemberId(Long memberId, Pageable pageable);

    Page<UserCoupon> findByCouponTemplateId(Long couponTemplateId, Pageable pageable);

    void update(UserCoupon userCoupon);

    boolean existsByMemberIdAndCouponTemplateId(Long memberId, Long couponTemplateId);

    Page<UserCouponItem> findByMemberIdWithTemplate(Long memberId, Pageable pageable);
}

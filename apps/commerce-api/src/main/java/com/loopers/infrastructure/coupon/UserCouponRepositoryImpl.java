package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        return userCouponJpaRepository.save(userCoupon);
    }

    @Override
    public boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId) {
        return userCouponJpaRepository.existsByUserIdAndCouponTemplateIdAndDeletedAtIsNull(userId, couponTemplateId);
    }

    @Override
    public List<UserCoupon> findAllByUserId(Long userId) {
        return userCouponJpaRepository.findAllByUserIdAndDeletedAtIsNull(userId);
    }

    @Override
    public Page<UserCoupon> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable) {
        return userCouponJpaRepository.findAllByCouponTemplateIdAndDeletedAtIsNull(couponTemplateId, pageable);
    }

    @Override
    public Optional<UserCoupon> findByIdAndUserId(Long id, Long userId) {
        return userCouponJpaRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId);
    }

    @Override
    public int useIfAvailable(Long id, Long userId, LocalDateTime now) {
        return userCouponJpaRepository.useIfAvailable(id, userId, now);
    }

    @Override
    public void deleteAllByCouponTemplateId(Long couponTemplateId) {
        userCouponJpaRepository.softDeleteAllByCouponTemplateId(couponTemplateId, ZonedDateTime.now());
    }
}

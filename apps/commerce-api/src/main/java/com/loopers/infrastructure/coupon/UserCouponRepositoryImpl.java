package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public UserCouponModel save(UserCouponModel userCoupon) {
        return userCouponJpaRepository.save(userCoupon);
    }

    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        return userCouponJpaRepository.existsByUserIdAndCouponId(userId, couponId);
    }

    @Override
    public Optional<UserCouponModel> findByIdAndUserIdForUpdate(Long id, Long userId) {
        return userCouponJpaRepository.findByIdAndUserIdForUpdate(id, userId);
    }

    @Override
    public List<UserCouponModel> findAllByUserId(Long userId) {
        return userCouponJpaRepository.findAllByUserId(userId);
    }

    @Override
    public Page<UserCouponModel> findAllByCouponId(Long couponId, Pageable pageable) {
        return userCouponJpaRepository.findAllByCouponId(couponId, pageable);
    }
}

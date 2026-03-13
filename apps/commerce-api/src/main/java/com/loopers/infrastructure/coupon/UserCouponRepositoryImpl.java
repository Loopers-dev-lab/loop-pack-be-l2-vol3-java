package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.page.PageQuery;
import com.loopers.support.page.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 발급 쿠폰 레포지토리 구현체.
 */
@Repository
@RequiredArgsConstructor
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public UserCouponModel save(UserCouponModel userCoupon) {
        return userCouponJpaRepository.save(userCoupon);
    }

    @Override
    public Optional<UserCouponModel> findById(Long userCouponId) {
        return userCouponJpaRepository.findById(userCouponId);
    }

    @Override
    public Optional<UserCouponModel> findByIdWithLock(Long userCouponId) {
        return userCouponJpaRepository.findByIdWithLock(userCouponId);
    }

    @Override
    public Optional<UserCouponModel> findByUserIdAndCouponId(Long userId, Long couponId) {
        return userCouponJpaRepository.findByUserIdAndCouponId(userId, couponId);
    }

    @Override
    public List<UserCouponModel> findAllByUserId(Long userId) {
        return userCouponJpaRepository.findAllByUserId(userId);
    }

    @Override
    public List<UserCouponModel> findAllByCouponId(Long couponId) {
        return userCouponJpaRepository.findAllByCouponId(couponId);
    }

    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        return userCouponJpaRepository.existsByUserIdAndCouponId(userId, couponId);
    }

    @Override
    public PagedResult<UserCouponModel> findAllByCouponIdPaged(Long couponId, PageQuery query) {
        Sort sort = query.ascending()
                ? Sort.by(Sort.Direction.ASC, query.sortField())
                : Sort.by(Sort.Direction.DESC, query.sortField());
        Page<UserCouponModel> page = userCouponJpaRepository.findAllByCouponId(
                couponId, PageRequest.of(query.page(), query.size(), sort));
        return new PagedResult<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Override
    public Optional<UserCouponModel> findByOrderId(Long orderId) {
        return userCouponJpaRepository.findByOrderId(orderId);
    }
}

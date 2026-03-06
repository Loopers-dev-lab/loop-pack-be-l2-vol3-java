package com.loopers.infrastructure.coupon.repository.impl;

import com.loopers.domain.coupon.model.UserCoupon;
import com.loopers.domain.coupon.model.UserCouponItem;
import com.loopers.domain.coupon.repository.UserCouponRepository;
import com.loopers.infrastructure.coupon.entity.UserCouponEntity;
import com.loopers.infrastructure.coupon.repository.UserCouponJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository jpaRepository;

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        UserCouponEntity entity = jpaRepository.save(UserCouponEntity.toEntity(userCoupon));
        return entity.toModel();
    }

    @Override
    public Optional<UserCoupon> findById(Long id) {
        return jpaRepository.findById(id).map(UserCouponEntity::toModel);
    }

    @Override
    public Page<UserCoupon> findByMemberId(Long memberId, Pageable pageable) {
        return jpaRepository.findByMemberId(memberId, pageable).map(UserCouponEntity::toModel);
    }

    @Override
    public Page<UserCoupon> findByCouponTemplateId(Long couponTemplateId, Pageable pageable) {
        return jpaRepository.findByCouponTemplateId(couponTemplateId, pageable).map(UserCouponEntity::toModel);
    }

    @Override
    public void update(UserCoupon userCoupon) {
        UserCouponEntity entity = jpaRepository.findById(userCoupon.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));

        entity.updateStatus(userCoupon.getStatus(), userCoupon.getUsedAt());
    }

    @Override
    public boolean existsByMemberIdAndCouponTemplateId(Long memberId, Long couponTemplateId) {
        return jpaRepository.existsByMemberIdAndCouponTemplateId(memberId, couponTemplateId);
    }

    @Override
    public Page<UserCouponItem> findByMemberIdWithTemplate(Long memberId, Pageable pageable) {
        return jpaRepository.findByMemberIdWithTemplate(memberId, pageable);
    }
}

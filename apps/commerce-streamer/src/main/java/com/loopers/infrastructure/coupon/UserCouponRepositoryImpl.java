package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public boolean existsByRefMemberIdAndRefCouponTemplateId(Long refMemberId, Long refCouponTemplateId) {
        return userCouponJpaRepository.existsByRefMemberIdAndRefCouponTemplateId(refMemberId, refCouponTemplateId);
    }

    @Override
    public UserCouponModel save(UserCouponModel model) {
        return userCouponJpaRepository.save(model);
    }
}

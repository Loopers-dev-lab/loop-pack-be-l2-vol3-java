package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponActionStatus;
import com.loopers.domain.coupon.CouponActionType;
import com.loopers.domain.coupon.CouponPendingActionModel;
import com.loopers.domain.coupon.CouponPendingActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 쿠폰 비동기 액션 레포지토리 구현체.
 */
@Repository
@RequiredArgsConstructor
public class CouponPendingActionRepositoryImpl implements CouponPendingActionRepository {

    private final CouponPendingActionJpaRepository couponPendingActionJpaRepository;

    @Override
    public CouponPendingActionModel save(CouponPendingActionModel action) {
        return couponPendingActionJpaRepository.save(action);
    }

    @Override
    public List<CouponPendingActionModel> findPending(int limit) {
        return couponPendingActionJpaRepository.findByStatusOrderByCreatedAtAsc(
                CouponActionStatus.PENDING, PageRequest.of(0, limit));
    }

    @Override
    public int cancelPendingConfirmsByUserCouponId(Long userCouponId) {
        return couponPendingActionJpaRepository.cancelPendingByUserCouponIdAndType(
                userCouponId, CouponActionType.CONFIRM);
    }

    @Override
    public int deleteDoneOlderThan(LocalDateTime threshold) {
        return couponPendingActionJpaRepository.deleteByStatusAndCreatedAtBefore(
                CouponActionStatus.DONE, threshold);
    }
}

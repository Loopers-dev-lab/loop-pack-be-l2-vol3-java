package com.loopers.domain.coupon;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 쿠폰 비동기 액션 레포지토리 인터페이스 (도메인 레이어).
 */
public interface CouponPendingActionRepository {

    CouponPendingActionModel save(CouponPendingActionModel action);

    /**
     * PENDING 상태의 액션을 최대 limit건 조회한다.
     */
    List<CouponPendingActionModel> findPending(int limit);

    /**
     * 특정 userCouponId에 대한 PENDING CONFIRM 액션을 CANCELLED로 전이한다.
     */
    int cancelPendingConfirmsByUserCouponId(Long userCouponId);

    /**
     * DONE 상태이고 생성일이 기준일 이전인 액션을 삭제한다.
     */
    int deleteDoneOlderThan(LocalDateTime threshold);
}

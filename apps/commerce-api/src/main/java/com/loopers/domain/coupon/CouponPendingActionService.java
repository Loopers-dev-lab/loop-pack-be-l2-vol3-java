package com.loopers.domain.coupon;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 쿠폰 비동기 액션 도메인 서비스.
 * <p>
 * 주문 생성 TX 내에서 CONFIRM/RESTORE 액션을 등록하고,
 * Relay가 폴링하여 처리할 PENDING 액션을 조회한다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponPendingActionService {

    private final CouponPendingActionRepository couponPendingActionRepository;

    /**
     * CONFIRM 액션을 등록한다. 반드시 상위 트랜잭션 내에서 호출해야 한다.
     *
     * @param userCouponId 발급 쿠폰 ID
     * @param orderId      주문 ID
     * @return 저장된 액션
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CouponPendingActionModel saveConfirm(Long userCouponId, Long orderId) {
        CouponPendingActionModel action = CouponPendingActionModel.confirm(userCouponId, orderId);
        return couponPendingActionRepository.save(action);
    }

    /**
     * RESTORE 액션을 등록한다. 반드시 상위 트랜잭션 내에서 호출해야 한다.
     *
     * @param userCouponId 발급 쿠폰 ID
     * @param orderId      주문 ID
     * @return 저장된 액션
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CouponPendingActionModel saveRestore(Long userCouponId, Long orderId) {
        CouponPendingActionModel action = CouponPendingActionModel.restore(userCouponId, orderId);
        return couponPendingActionRepository.save(action);
    }

    /**
     * PENDING 상태의 액션을 최대 limit건 조회한다.
     */
    public List<CouponPendingActionModel> findPending(int limit) {
        return couponPendingActionRepository.findPending(limit);
    }

    /**
     * 특정 userCouponId에 대한 PENDING CONFIRM 액션을 취소한다.
     * 주문 취소/만료 시 아직 처리되지 않은 CONFIRM 액션을 무효화하기 위해 사용된다.
     *
     * @param userCouponId 발급 쿠폰 ID
     * @return 취소된 액션 수
     */
    @Transactional
    public int cancelPendingConfirms(Long userCouponId) {
        return couponPendingActionRepository.cancelPendingConfirmsByUserCouponId(userCouponId);
    }
}

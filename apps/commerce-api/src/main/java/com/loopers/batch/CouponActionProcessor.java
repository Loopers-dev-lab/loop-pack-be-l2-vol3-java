package com.loopers.batch;

import com.loopers.domain.coupon.CouponPendingActionModel;
import com.loopers.domain.coupon.CouponPendingActionRepository;
import com.loopers.domain.coupon.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 비동기 액션 프로세서.
 * <p>
 * 개별 액션을 트랜잭션 내에서 처리하며,
 * 최대 재시도 횟수 초과 시 FAILED 상태로 전이한다.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CouponActionProcessor {

    private static final int MAX_RETRY = 5;

    private final CouponService couponService;
    private final CouponPendingActionRepository couponPendingActionRepository;

    /**
     * 쿠폰 액션을 처리한다.
     * CONFIRM: RESERVED → USED 확정
     * RESTORE: RESERVED → AVAILABLE 복원
     */
    @Transactional
    public void process(CouponPendingActionModel action) {
        action.incrementRetry();

        try {
            switch (action.getActionType()) {
                case CONFIRM -> couponService.confirmCouponUsed(
                        action.getUserCouponId(), action.getOrderId());
                case RESTORE -> couponService.restoreCouponByAction(action.getUserCouponId());
            }
            action.markDone();
            log.debug("쿠폰 액션 처리 완료: actionId={}, type={}", action.getActionId(), action.getActionType());
        } catch (Exception e) {
            if (action.getRetryCount() >= MAX_RETRY) {
                action.markFailed(truncate(e.getMessage(), 500));
                log.error("쿠폰 액션 최대 재시도 초과: actionId={}, type={}", action.getActionId(), action.getActionType(), e);
            } else {
                log.warn("쿠폰 액션 처리 실패 (재시도 {}/{}): actionId={}, type={}",
                        action.getRetryCount(), MAX_RETRY, action.getActionId(), action.getActionType(), e);
            }
        }

        couponPendingActionRepository.save(action);
    }

    private String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}

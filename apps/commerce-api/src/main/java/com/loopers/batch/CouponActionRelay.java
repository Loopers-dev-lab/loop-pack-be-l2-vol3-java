package com.loopers.batch;

import com.loopers.domain.coupon.CouponPendingActionModel;
import com.loopers.domain.coupon.CouponPendingActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 쿠폰 비동기 액션 Relay.
 * <p>
 * 3초 간격으로 PENDING 상태의 쿠폰 액션을 폴링하여
 * {@link CouponActionProcessor}에 위임한다.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CouponActionRelay {

    private static final int POLL_LIMIT = 50;

    private final CouponPendingActionService couponPendingActionService;
    private final CouponActionProcessor couponActionProcessor;

    @Scheduled(fixedDelay = 3000)
    public void relay() {
        List<CouponPendingActionModel> actions = couponPendingActionService.findPending(POLL_LIMIT);
        if (actions.isEmpty()) {
            return;
        }
        log.info("쿠폰 액션 Relay: {}건 처리 시작", actions.size());
        int successCount = 0;
        for (CouponPendingActionModel action : actions) {
            try {
                couponActionProcessor.process(action);
                successCount++;
            } catch (Exception e) {
                log.warn("쿠폰 액션 처리 실패: actionId={}, type={}", action.getActionId(), action.getActionType(), e);
            }
        }
        log.info("쿠폰 액션 Relay 완료: 성공 {}/전체 {}", successCount, actions.size());
    }
}

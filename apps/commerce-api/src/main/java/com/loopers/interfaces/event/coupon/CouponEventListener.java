package com.loopers.interfaces.event.coupon;

import java.util.Objects;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.loopers.domain.coupon.OwnedCouponService;
import com.loopers.domain.order.OrderEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 쿠폰 도메인의 이벤트 리스너.
 *
 * <p>쿠폰 도메인에 영향을 주는 이벤트를 수신하여 후속 처리를 수행한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponEventListener {

    private final OwnedCouponService ownedCouponService;

    /**
     * 주문 실패 이벤트를 처리한다.
     *
     * <p>쿠폰이 적용된 주문이면 쿠폰을 복원한다.</p>
     *
     * @param event 주문 실패 이벤트
     */
    @Async
    @TransactionalEventListener
    public void handle(OrderEvent.OrderFailed event) {
        if (Objects.isNull(event.ownedCouponId())) {
            return;
        }
        try {
            ownedCouponService.restore(event.ownedCouponId());
        } catch (Exception e) {
            log.error("쿠폰 복원 실패 [orderId={}, ownedCouponId={}]", event.orderId(), event.ownedCouponId(), e);
        }
    }
}

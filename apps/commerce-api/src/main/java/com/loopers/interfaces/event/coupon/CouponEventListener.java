package com.loopers.interfaces.event.coupon;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.loopers.domain.coupon.OwnedCouponService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderEvent;
import com.loopers.domain.order.OrderService;

import lombok.RequiredArgsConstructor;

/**
 * 쿠폰 도메인의 이벤트 리스너.
 *
 * <p>쿠폰 관련 이벤트를 수신하여 쿠폰 도메인의 후속 처리를 수행한다.</p>
 */
@Component
@RequiredArgsConstructor
public class CouponEventListener {

    private final OrderService orderService;
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
        Order order = orderService.getById(event.orderId());
        if (order.hasAppliedCoupon()) {
            ownedCouponService.restore(order.getOwnedCouponId());
        }
    }
}

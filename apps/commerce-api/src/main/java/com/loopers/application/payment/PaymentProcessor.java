package com.loopers.application.payment;

import com.loopers.application.coupon.IssuedCouponService;
import com.loopers.application.order.OrderService;
import com.loopers.application.stock.StockService;
import com.loopers.domain.order.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentProcessor {

    private final PaymentService paymentService;
    private final StockService stockService;
    private final IssuedCouponService issuedCouponService;
    private final OrderService orderService;

    /**
     * 결제 성공 시 비즈니스 확정
     * - 재고 확정, 주문 PAID
     * 호출 측에서 트랜잭션 보장 필요
     */
    public void confirm(Order order) {
        stockService.confirm(order.getProductQuantities());
        orderService.payOrder(order.getId());
    }

    /**
     * 결제 실패 처리 + 비즈니스 보상 (원자적)
     * 호출 측에서 트랜잭션 보장 필요
     */
    public void failAndCompensate(Long paymentId, Long orderId, String reason) {
        paymentService.markFailed(paymentId, reason);
        compensate(orderService.getOrder(orderId));
    }

    /**
     * 결제 취소 처리 + 비즈니스 보상 (원자적)
     * 호출 측에서 트랜잭션 보장 필요
     */
    public void cancelAndCompensate(Long paymentId, Long orderId, String reason) {
        paymentService.markCanceled(paymentId, reason);
        compensate(orderService.getOrder(orderId));
    }

    private void compensate(Order order) {
        stockService.releaseConfirmed(order.getProductQuantities());

        if (order.getIssuedCouponId() != null) {
            issuedCouponService.restore(order.getIssuedCouponId());
        }
        orderService.cancelOrder(order.getId());
    }
}

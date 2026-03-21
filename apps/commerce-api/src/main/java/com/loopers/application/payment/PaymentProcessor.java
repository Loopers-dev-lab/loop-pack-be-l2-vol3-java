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
     * PG 승인 성공 → 비즈니스 확정 (원자적)
     * - Payment SUCCEEDED + 재고 확정 + 주문 PAID
     * 호출 측에서 트랜잭션 보장 필요
     */
    public void confirmAndSettle(Long paymentId, Long orderId) {
        if (!paymentService.markSucceededIfRequested(paymentId)) return;
        Order order = orderService.getOrder(orderId);
        stockService.confirm(order.getProductQuantities());
        orderService.payOrder(orderId);
    }

    /**
     * PG 승인 실패 → 예약 해제 (원자적)
     * - Payment FAILED + 재고 예약 해제 + 쿠폰 복원 + 주문 CANCELED
     * 호출 측에서 트랜잭션 보장 필요
     */
    public void failAndRelease(Long paymentId, Long orderId, String reason) {
        if (!paymentService.markFailedIfRequested(paymentId, reason)) return;
        Order order = orderService.getOrder(orderId);
        stockService.releaseReserved(order.getProductQuantities());
        if (order.getIssuedCouponId() != null) {
            issuedCouponService.restore(order.getIssuedCouponId());
        }
        orderService.cancelOrder(orderId);
    }

    /**
     * 결제 취소 확정 + 비즈니스 보상 (원자적)
     * - Payment CANCELED + 확정 재고 복원 + 쿠폰 복원 + 주문 CANCELED
     * 호출 측에서 트랜잭션 보장 필요
     */
    public void cancelAndCompensate(Long paymentId, Long orderId) {
        if (!paymentService.markCanceledIfRequested(paymentId)) return;
        Order order = orderService.getOrder(orderId);
        stockService.releaseConfirmed(order.getProductQuantities());
        if (order.getIssuedCouponId() != null) {
            issuedCouponService.restore(order.getIssuedCouponId());
        }
        orderService.cancelOrder(orderId);
    }
}

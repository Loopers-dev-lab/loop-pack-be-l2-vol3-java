package com.loopers.application.payment;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.coupon.OwnedCouponService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.ProductService;

import lombok.RequiredArgsConstructor;

/**
 * 결제 후속 처리 및 복구를 수행하는 내부 컴포넌트.
 *
 * <p>결제 성공/실패 시 주문 상태 변경, 쿠폰 사용, 재고 복원 등의
 * 공통 후속 처리를 담당한다. READY 상태로 방치된 결제의 복구도 수행한다.</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentProcessor {

    private final OrderService orderService;
    private final OwnedCouponService ownedCouponService;
    private final ProductService productService;
    private final PaymentService paymentService;

    /**
     * 결제 성공 후속 처리를 수행한다.
     *
     * <p>주문을 결제 완료 상태로 변경하고, 쿠폰이 적용된 주문이면 쿠폰을 사용 처리한다.</p>
     *
     * @param orderId 결제 대상 주문 ID
     */
    public void handleSuccess(Long orderId) {
        Order order = orderService.pay(orderId);

        if (order.hasAppliedCoupon()) {
            ownedCouponService.use(order.getOwnedCouponId());
        }
    }

    /**
     * 결제 실패 후속 처리를 수행한다.
     *
     * <p>주문을 실패 상태로 변경하고, 주문 항목별로 차감된 재고를 복원한다.</p>
     *
     * @param orderId 결제 대상 주문 ID
     */
    public void handleFailure(Long orderId) {
        Order order = orderService.fail(orderId);

        order.getOrderItems().forEach(item ->
                productService.restoreStock(item.getProductId(), item.getQuantity())
        );
    }

    /**
     * PG 거래가 존재하는 READY 결제를 복구한다.
     *
     * <p>결제를 확정(READY → PENDING)한 뒤 최종 상태를 반영하고,
     * 상태에 따라 성공/실패 후속 처리를 수행한다.</p>
     *
     * @param paymentId      결제 ID
     * @param transactionKey PG 거래 키
     * @param status         최종 결제 상태 (SUCCESS 또는 FAILED)
     * @param reason         사유 (실패 시)
     */
    @Transactional
    public void recoverWithTransaction(Long paymentId, String transactionKey, PaymentStatus status, String reason) {
        var payment = paymentService.confirmPayment(paymentId, transactionKey);
        payment.update(status, reason);

        switch (status) {
            case SUCCESS -> handleSuccess(payment.getOrderId());
            case FAILED -> handleFailure(payment.getOrderId());
        }
    }

    /**
     * PG 거래가 없는 READY 결제를 실패 처리한다.
     *
     * @param paymentId 결제 ID
     * @param reason    실패 사유
     */
    @Transactional
    public void recoverWithoutTransaction(Long paymentId, String reason) {
        var payment = paymentService.fail(paymentId, reason);
        handleFailure(payment.getOrderId());
    }
}

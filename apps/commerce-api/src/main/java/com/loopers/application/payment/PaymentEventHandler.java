package com.loopers.application.payment;

import com.loopers.application.order.event.OrderPaymentEvent;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.dto.OrderInfo;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgPaymentResult;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventHandler {

    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback";

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final OrderService orderService;
    private final ProductService productService;
    private final CouponService couponService;
    private final UserService userService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderPaymentEvent event) {
        try {
            PaymentModel payment = paymentService.createPending(
                    event.orderId(), event.totalPrice(),
                    event.cardType(), maskCardNo(event.cardNo()));

            PgPaymentResult pgResult = paymentGateway.requestPayment(
                    new PgPaymentRequest(
                            String.format("%06d", event.orderId()),
                            event.cardType().name(),
                            event.cardNo(),
                            event.totalPrice(),
                            CALLBACK_URL,
                            String.valueOf(event.userId())));

            if (pgResult.requested()) {
                paymentService.updateRequested(payment.getId(), pgResult.transactionKey());
                orderService.completeOrder(event.orderId());
            } else {
                paymentService.failById(payment.getId());
                compensateOrder(event);
            }
        } catch (Exception e) {
            log.error("결제 처리 실패 — orderId={}", event.orderId(), e);
            compensateOrder(event);
        }
    }

    private void compensateOrder(OrderPaymentEvent event) {
        try {
            OrderInfo.PaymentFailureCancellation cancellation =
                    orderService.cancelByPaymentFailure(event.orderId());

            for (OrderInfo.PaymentFailureCancellation.CancelledItem item : cancellation.items()) {
                productService.increaseStock(item.productId(), item.quantity());
            }
            couponService.restoreByOrderId(event.orderId());
            userService.addPoint(cancellation.userId(), cancellation.totalPrice());
        } catch (Exception e) {
            log.error("보상 트랜잭션 실패 — orderId={}", event.orderId(), e);
        }
    }

    private String maskCardNo(String cardNo) {
        if (cardNo == null || cardNo.length() < 4) {
            return cardNo;
        }
        return "****-****-****-" + cardNo.substring(cardNo.length() - 4);
    }
}

package com.loopers.application.payment;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.OwnedCouponService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;

import lombok.RequiredArgsConstructor;

/**
 * PG로부터 수신한 결제 콜백을 처리한다.
 *
 * <p>결제 성공 시 결제 상태 변경, 주문 완료, 쿠폰 사용 처리를 하나의 트랜잭션으로 수행한다.
 * 이미 처리된 결제는 멱등성을 위해 무시한다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class HandlePaymentCallbackUseCase {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final OwnedCouponService ownedCouponService;
    private final ProductService productService;

    /**
     * @param command 결제 콜백 커맨드 (transactionKey, status, reason)
     * @throws CoreException 결제가 존재하지 않는 경우
     */
    @Transactional
    public void execute(PaymentCallbackCommand command) {
        Payment payment = paymentService.getByTransactionKey(command.transactionKey());

        if (payment.isProcessed()) {
            return;
        }

        payment.update(command.status(), command.reason());

        switch (command.status()) {
            case SUCCESS -> handleSuccess(payment);
            case FAILED -> handleFailure(payment);
        }
    }

    private void handleSuccess(Payment payment) {
        Order order = orderService.pay(payment.getOrderId());

        if (order.hasCoupon()) {
            ownedCouponService.use(order.getOwnedCouponId());
        }
    }

    private void handleFailure(Payment payment) {
        Order order = orderService.fail(payment.getOrderId());

        order.getOrderItems().forEach(item ->
                productService.restoreStock(item.getProductId(), item.getQuantity())
        );
    }
}

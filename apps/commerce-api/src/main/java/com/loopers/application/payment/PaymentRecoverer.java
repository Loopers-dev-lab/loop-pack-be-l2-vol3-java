package com.loopers.application.payment;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;

import lombok.RequiredArgsConstructor;

/**
 * READY 상태로 방치된 결제를 복구하는 컴포넌트.
 *
 * <p>PG 거래 존재 여부에 따라 결제를 확정하거나 실패 처리하고,
 * 결제 상태에 따라 주문 상태를 변경한다.
 * 재고 복원, 쿠폰 복원 등 보상 처리는 {@link com.loopers.domain.order.OrderEvent.OrderFailed}
 * 이벤트를 통해 비동기로 수행된다.</p>
 */
@Component
@RequiredArgsConstructor
public class PaymentRecoverer {

    private final OrderService orderService;
    private final PaymentService paymentService;

    /**
     * PG 거래가 존재하는 READY 결제를 복구한다.
     *
     * <p>결제를 확정(READY → PENDING)한 뒤 최종 상태를 반영하고,
     * 상태에 따라 주문을 성공/실패 처리한다.</p>
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
            case SUCCESS -> orderService.pay(payment.getOrderId());
            case FAILED -> orderService.fail(payment.getOrderId());
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
        orderService.fail(payment.getOrderId());
    }
}

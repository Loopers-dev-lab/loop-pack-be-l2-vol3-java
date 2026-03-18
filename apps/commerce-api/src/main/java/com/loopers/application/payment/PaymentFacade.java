package com.loopers.application.payment;

import com.loopers.application.order.OrderApp;
import com.loopers.domain.payment.PgStatus;
import com.loopers.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentApp paymentApp;
    private final OrderApp orderApp;

    public PaymentInfo requestPayment(PaymentCommand command, String callbackUrl) {
        PaymentInfo pending = paymentApp.createPendingPayment(command);
        return paymentApp.requestToGateway(pending.id(), callbackUrl);
    }

    public void handleCallback(String pgTransactionId, PgStatus pgStatus, BigDecimal pgAmount) {
        PaymentInfo payment = paymentApp.handleCallback(pgTransactionId, pgStatus, pgAmount);
        if (payment.isCompleted()) {
            try {
                orderApp.markOrderPaid(payment.orderId());
            } catch (CoreException e) {
                log.warn("Order 상태 업데이트 실패 (도메인 오류) — 확인 필요. paymentId={}, orderId={}, error={}",
                        payment.id(), payment.orderId(), e.getMessage());
            }
        }
    }

    public PaymentInfo syncPayment(Long paymentId, Long memberId) {
        PaymentInfo payment = paymentApp.syncFromGateway(paymentId, memberId);
        if (payment.isCompleted()) {
            try {
                orderApp.markOrderPaid(payment.orderId());
            } catch (CoreException e) {
                log.warn("Order 상태 업데이트 실패 (도메인 오류) — 확인 필요. paymentId={}, orderId={}, error={}",
                        payment.id(), payment.orderId(), e.getMessage());
            } catch (Exception e) {
                log.error("Order 상태 업데이트 실패 — 확인 필요. paymentId={}, orderId={}", payment.id(), payment.orderId(), e);
            }
        }
        return payment;
    }
}

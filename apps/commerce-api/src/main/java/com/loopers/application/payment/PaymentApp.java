package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PgResult;
import com.loopers.domain.payment.PgStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class PaymentApp {

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    @Transactional
    public PaymentInfo createPendingPayment(PaymentCommand command) {
        PaymentModel payment = paymentService.createPending(
            command.orderId(), command.memberId(), command.cardType(), command.cardNo(), command.amount()
        );
        return PaymentInfo.from(payment);
    }

    public PaymentInfo requestToGateway(Long paymentId, String callbackUrl) {
        PaymentModel payment = paymentService.getById(paymentId);
        PgResult result = paymentGateway.requestPayment(payment, callbackUrl);
        if (result.isAccepted()) {
            return PaymentInfo.from(paymentService.updateRequested(paymentId, result.pgTransactionKey()));
        }
        return PaymentInfo.from(payment);
    }

    @Transactional
    public PaymentInfo handleCallback(String pgTransactionId, PgStatus pgStatus, BigDecimal pgAmount) {
        if (pgStatus == PgStatus.SUCCESS) {
            return PaymentInfo.from(paymentService.updateCompleted(pgTransactionId, pgAmount));
        }
        return PaymentInfo.from(paymentService.updateFailed(pgTransactionId));
    }

    public PaymentInfo syncFromGateway(Long paymentId, Long memberId) {
        PaymentModel payment = paymentService.getById(paymentId);
        if (payment.getPgTransactionId() != null) {
            return syncByTransactionId(payment, memberId);
        }
        return syncByOrderId(payment, memberId);
    }

    private PaymentInfo syncByTransactionId(PaymentModel payment, Long memberId) {
        PgResult result = paymentGateway.getPaymentResult(payment.getPgTransactionId(), memberId);
        if (result.isSuccess()) {
            return PaymentInfo.from(paymentService.updateCompleted(payment.getPgTransactionId(), result.amount()));
        }
        if (result.isUnavailable()) {
            return PaymentInfo.from(payment);
        }
        return PaymentInfo.from(paymentService.updateFailed(payment.getPgTransactionId()));
    }

    private PaymentInfo syncByOrderId(PaymentModel payment, Long memberId) {
        PgResult result = paymentGateway.getPaymentByOrderId(payment.getRefOrderId().value(), memberId);
        if (result.isUnavailable()) {
            return PaymentInfo.from(payment);
        }
        String transactionKey = result.pgTransactionKey();
        PaymentModel requested = paymentService.updateRequested(payment.getId(), transactionKey);
        if (result.isSuccess()) {
            return PaymentInfo.from(paymentService.updateCompleted(transactionKey, payment.getAmount()));
        }
        if (result.isAccepted()) {
            return PaymentInfo.from(requested);
        }
        return PaymentInfo.from(paymentService.updateFailed(transactionKey));
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPayment(Long paymentId, Long memberId) {
        return PaymentInfo.from(paymentService.getById(paymentId));
    }

    public PaymentInfo forceFailPayment(Long paymentId) {
        return PaymentInfo.from(paymentService.forceFailById(paymentId));
    }
}

package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.PaymentErrorType;

public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Payment create(Long orderId, int requestedAmount, String paymentMethod, String idempotencyKey) {
        Payment payment = Payment.create(orderId, requestedAmount, paymentMethod, idempotencyKey);
        return paymentRepository.save(payment);
    }

    public void approve(Long paymentId, String pgTxnId, int approvedAmount) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(PaymentErrorType.PAYMENT_NOT_FOUND));
        payment.approve(pgTxnId, approvedAmount);
    }

    public void fail(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(PaymentErrorType.PAYMENT_NOT_FOUND));
        payment.fail();
    }
}

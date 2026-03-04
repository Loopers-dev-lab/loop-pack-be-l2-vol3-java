package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.PaymentErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment create(Long orderId, int requestedAmount, String paymentMethod, String idempotencyKey) {
        Payment payment = Payment.create(orderId, requestedAmount, paymentMethod, idempotencyKey);
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment getById(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(PaymentErrorType.PAYMENT_NOT_FOUND));
    }

    @Transactional
    public void approve(Long paymentId, String pgTxnId, int approvedAmount) {
        Payment payment = getById(paymentId);
        payment.approve(pgTxnId, approvedAmount);
        paymentRepository.save(payment);
    }

    @Transactional
    public void fail(Long paymentId) {
        Payment payment = getById(paymentId);
        payment.fail();
        paymentRepository.save(payment);
    }
}

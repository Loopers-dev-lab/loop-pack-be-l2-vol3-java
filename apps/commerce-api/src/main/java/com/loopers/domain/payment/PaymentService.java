package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment createPayment(Long orderId) {
        Payment payment = Payment.create(orderId);
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment getById(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ErrorMessage.Payment.PAYMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Payment getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ErrorMessage.Payment.PAYMENT_NOT_FOUND));
    }

    @Transactional
    public Payment markSuccess(Long paymentId, String pgTransactionId) {
        Payment payment = getById(paymentId);
        payment.markSuccess(pgTransactionId);
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment markFailed(Long paymentId, String failReason) {
        Payment payment = getById(paymentId);
        payment.markFailed(failReason);
        return paymentRepository.save(payment);
    }
}

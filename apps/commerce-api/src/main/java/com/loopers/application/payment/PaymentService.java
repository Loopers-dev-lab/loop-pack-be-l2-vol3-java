package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    // Command

    @Transactional
    public Payment createPayment(Long orderId, Long userId, CardType cardType, String cardNo, BigDecimal amount) {
        Payment payment = Payment.create(orderId, userId, cardType, cardNo, amount);
        return paymentRepository.save(payment);
    }

    @Transactional
    public void markSucceeded(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다"));
        payment.markSucceeded();
    }

    @Transactional
    public void markFailed(Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다"));
        payment.markFailed(reason);
    }

    @Transactional
    public void markCanceled(Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다"));
        payment.markCanceled(reason);
    }

    // Query

    @Transactional(readOnly = true)
    public Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다"));
    }

    @Transactional(readOnly = true)
    public Optional<Payment> getPaymentByPaymentKey(String paymentKey) {
        return paymentRepository.findByPaymentKey(paymentKey);
    }

    @Transactional(readOnly = true)
    public Optional<Payment> getLatestPaymentByOrderId(Long orderId) {
        return paymentRepository.findLatestByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public boolean existsActivePayment(Long orderId) {
        return paymentRepository.existsActiveByOrderId(orderId);
    }
}

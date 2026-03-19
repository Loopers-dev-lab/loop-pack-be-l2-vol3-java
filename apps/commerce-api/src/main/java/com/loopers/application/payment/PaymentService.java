package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    // Command

    @Transactional
    public Payment createPayment(PaymentCommand.Create command) {
        Payment payment = Payment.create(
                command.orderId(), command.userId(), command.pgType(),
                command.cardType(), command.cardNo(), command.amount());
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

    @Transactional(readOnly = true)
    public boolean existsSucceededPayment(Long orderId) {
        return paymentRepository.existsSucceededByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public List<Payment> findRequestedOlderThan(ZonedDateTime threshold) {
        return paymentRepository.findByStatusOlderThan(PaymentStatus.REQUESTED, threshold);
    }
}

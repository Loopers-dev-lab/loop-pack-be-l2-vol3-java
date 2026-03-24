package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentAppService {
    private final PaymentRepository paymentRepository;

    public Payment createPayment(Long orderId, Long userId, String cardType, String cardNo, Money amount) {
        paymentRepository.findByOrderIdAndStatusIn(orderId, List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS))
                .ifPresent(existing -> {
                    throw new CoreException(ErrorType.CONFLICT, "이미 진행 중이거나 완료된 결제가 존재합니다. orderId=" + orderId);
                });

        Payment payment = Payment.create(orderId, userId, cardType, cardNo, amount);
        return paymentRepository.save(payment);
    }

    public Payment completePayment(Long paymentId, String transactionId, String pgMessage) {
        Payment payment = getById(paymentId);
        payment.complete(transactionId, pgMessage);
        return payment;
    }

    public Payment failPayment(Long paymentId, String pgMessage) {
        Payment payment = getById(paymentId);
        payment.fail(pgMessage);
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment getById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Payment getByOrderIdAndActiveStatus(Long orderId) {
        return paymentRepository.findByOrderIdAndStatusIn(orderId, List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 주문의 결제를 찾을 수 없습니다. orderId=" + orderId));
    }

    @Transactional(readOnly = true)
    public List<Payment> getPendingPayments() {
        return paymentRepository.findByStatus(PaymentStatus.PENDING);
    }
}

package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment createPending(Long orderId, Long memberId, CardType cardType, String cardNo, long amount) {
        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            throw new CoreException(ErrorType.ORDER_ALREADY_PAID);
        }
        Payment payment = new Payment(orderId, memberId, cardType, cardNo, amount, PaymentStatus.PENDING, null);
        return paymentRepository.save(payment);
    }

    @Transactional
    public void handleCallback(Long orderId, String transactionId, boolean success) {
        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
        if (success) {
            payment.complete(transactionId);
        } else {
            payment.fail();
        }
    }

    @Transactional(readOnly = true)
    public Payment getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }
}
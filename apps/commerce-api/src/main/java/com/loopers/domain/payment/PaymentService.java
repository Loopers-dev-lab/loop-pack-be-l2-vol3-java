package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment createPayment(Long orderId, Long userId, BigDecimal amount, CardType cardType, String cardNo) {
        Payment payment = Payment.create(orderId, userId, amount, cardType, cardNo);
        return paymentRepository.save(payment);
    }

    public Payment getById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다."));
    }

    public Payment getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 주문의 결제를 찾을 수 없습니다."));
    }

    public List<Payment> getPendingPayments() {
        return paymentRepository.findAllByStatusIn(
                List.of(PaymentStatus.REQUESTED, PaymentStatus.PENDING)
        );
    }
}

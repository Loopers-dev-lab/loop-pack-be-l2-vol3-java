package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentQueryApplicationService {

    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByOrder(String memberId, UUID orderId) {
        List<Payment> payments = paymentRepository.findAllByOrderId(orderId).stream()
                .filter(payment -> memberId.equals(payment.memberId()))
                .sorted(Comparator.comparing(Payment::updatedAt).reversed())
                .toList();

        if (payments.isEmpty()) {
            throw new CoreException(ErrorType.NOT_FOUND, "결제 내역을 찾을 수 없습니다.");
        }

        return payments;
    }
}

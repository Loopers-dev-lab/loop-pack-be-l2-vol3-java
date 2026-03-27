package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentModel create(Long orderId, Long memberId, int amount, String cardType, String cardNo) {
        // 중복 결제 방지: 진행 중이거나 완료된 결제가 있으면 거부
        paymentRepository.findByOrderId(orderId).ifPresent(existing -> {
            if (existing.getStatus() != PaymentStatus.FAILED) {
                throw new CoreException(ErrorType.CONFLICT, "이미 결제가 진행 중이거나 완료된 주문입니다.");
            }
        });
        PaymentModel payment = new PaymentModel(orderId, memberId, amount, cardType, cardNo);
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public PaymentModel getById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다."));
    }

    @Transactional(readOnly = true)
    public PaymentModel getByTransactionId(String transactionId) {
        return paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "거래 ID에 해당하는 결제가 없습니다: " + transactionId));
    }

    @Transactional(readOnly = true)
    public PaymentModel getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "주문에 해당하는 결제가 없습니다: " + orderId));
    }

    @Transactional(readOnly = true)
    public List<PaymentModel> findStalledPayments() {
        return paymentRepository.findAllByStatusIn(
                List.of(PaymentStatus.PENDING, PaymentStatus.TIMED_OUT));
    }

    @Transactional
    public void markSuccess(Long paymentId, String transactionId) {
        PaymentModel payment = getById(paymentId);
        payment.markSuccess(transactionId);
    }

    @Transactional
    public void markFailed(Long paymentId, String failReason) {
        PaymentModel payment = getById(paymentId);
        payment.markFailed(failReason);
    }

    @Transactional
    public void markTimedOut(Long paymentId, String transactionId) {
        PaymentModel payment = getById(paymentId);
        payment.markTimedOut(transactionId);
    }

    @Transactional
    public void assignTransactionId(Long paymentId, String transactionId) {
        PaymentModel payment = getById(paymentId);
        payment.assignTransactionId(transactionId);
    }
}

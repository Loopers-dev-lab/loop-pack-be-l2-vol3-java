package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.List;
import java.util.Optional;

public class PaymentDomainService {

    private final PaymentRepository paymentRepository;

    public PaymentDomainService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Payment createPayment(Long orderId, Long userId, CardType cardType, String cardNo, int amount) {
        Payment payment = new Payment(orderId, userId, cardType, cardNo, amount);
        return paymentRepository.save(payment);
    }

    public Payment getById(Long id) {
        return paymentRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }

    public Payment getByIdForUpdate(Long id) {
        return paymentRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
    }

    public Payment getByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "트랜잭션 키에 해당하는 결제 정보를 찾을 수 없습니다. transactionKey=" + transactionKey));
    }

    public Payment getByTransactionKeyForUpdate(String transactionKey) {
        return paymentRepository.findByTransactionKeyForUpdate(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "트랜잭션 키에 해당하는 결제 정보를 찾을 수 없습니다. transactionKey=" + transactionKey));
    }

    public Optional<Payment> findByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey);
    }

    public List<Payment> findPendingByOrderIdAndUserIdForUpdate(Long orderId, Long userId) {
        return paymentRepository.findPendingByOrderIdAndUserIdForUpdate(orderId, userId);
    }

    public List<Payment> getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    public List<Payment> getPendingAndInProgressPayments() {
        return paymentRepository.findByStatusIn(
            List.of(PaymentStatus.PENDING, PaymentStatus.IN_PROGRESS)
        );
    }

    public Payment save(Payment payment) {
        return paymentRepository.save(payment);
    }
}

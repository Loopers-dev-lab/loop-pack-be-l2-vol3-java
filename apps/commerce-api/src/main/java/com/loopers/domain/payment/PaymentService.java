package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentModel createPending(Long orderId, int amount,
                                      CardType cardType, String maskedCardNo) {

        paymentRepository.findByOrderId(orderId)
                .ifPresent(existing -> {
                    throw new CoreException(PaymentErrorCode.PAYMENT_IN_PROGRESS);
                });
        return paymentRepository.save(
                PaymentModel.create(orderId, amount, cardType, maskedCardNo));
    }

    @Transactional
    public PaymentModel updateRequested(Long paymentId, String pgTransactionId) {
        PaymentModel payment = getByIdOrThrow(paymentId);
        payment.requested(pgTransactionId);
        return payment;
    }

    @Transactional
    public PaymentModel updateCompleted(String pgTransactionId) {
        PaymentModel payment = getByPgTransactionIdOrThrow(pgTransactionId);
        if (payment.isTerminal()) {
            return payment;
        }
        payment.complete();
        return payment;
    }

    @Transactional
    public PaymentModel updateFailed(String pgTransactionId, String failureCode,
                                     String failureMessage) {
        PaymentModel payment = getByPgTransactionIdOrThrow(pgTransactionId);
        if (payment.isTerminal()) {
            return payment;
        }
        payment.fail(failureCode, failureMessage);
        return payment;
    }

    @Transactional
    public void failById(Long paymentId) {
        getByIdOrThrow(paymentId).fail();
    }

    @Transactional(readOnly = true)
    public Optional<PaymentModel> findByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    private PaymentModel getByIdOrThrow(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new CoreException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    private PaymentModel getByPgTransactionIdOrThrow(String pgTransactionId) {
        return paymentRepository.findByPgTransactionId(pgTransactionId)
                .orElseThrow(() -> new CoreException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }
}

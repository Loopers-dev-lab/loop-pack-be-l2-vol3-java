package com.loopers.application.payment;

import com.loopers.application.payment.command.StartPaymentCommand;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentStartPreparationApplicationService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentStartPreparationResult prepare(StartPaymentCommand command) {
        Payment existing = paymentRepository.findByMemberIdAndOrderId(command.memberId(), command.orderId())
                .orElse(null);

        if (existing != null && isAlreadyStarted(existing)) {
            return new PaymentStartPreparationResult(existing, false);
        }

        Payment paymentToRequest = existing != null
                ? existing
                : new Payment(
                command.memberId(),
                command.orderId(),
                command.cardType(),
                command.cardNo(),
                command.amount()
        );

        try {
            return new PaymentStartPreparationResult(paymentRepository.save(paymentToRequest), true);
        } catch (DataIntegrityViolationException e) {
            Payment payment = paymentRepository.findByMemberIdAndOrderId(command.memberId(), command.orderId())
                    .orElseThrow(() -> new CoreException(
                            ErrorType.CONFLICT,
                            "결제를 생성하지 못했습니다. 잠시 후 다시 시도해 주세요."
                    ));
            return new PaymentStartPreparationResult(payment, false);
        }
    }

    public boolean isAlreadyStarted(Payment payment) {
        return payment.status() == PaymentStatus.REQUESTED
                || payment.status() == PaymentStatus.SUCCEEDED
                || payment.status() == PaymentStatus.CANCEL_REQUESTED
                || payment.status() == PaymentStatus.CANCEL_RECONCILE_REQUIRED
                || payment.status() == PaymentStatus.CANCELLED;
    }
}

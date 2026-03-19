package com.loopers.infrastructure.payment;

import com.loopers.application.payment.PaymentCompletionApplicationService;
import com.loopers.application.payment.command.CompletePaymentCommand;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "loopers.payment.completion.polling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentCompletionPollingScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentCompletionApplicationService paymentCompletionApplicationService;

    @Scheduled(fixedDelayString = "${loopers.payment.completion.polling.fixed-delay-ms:5000}")
    public void pollPendingPayments() {
        List<Payment> candidates = paymentRepository.findAllByStatusIn(
                List.of(PaymentStatus.CANCEL_RECONCILE_REQUIRED)
        );

        for (Payment payment : candidates) {
            if (payment.pgTransactionKey() == null || payment.pgTransactionKey().isBlank()) {
                continue;
            }
            paymentCompletionApplicationService.complete(
                    new CompletePaymentCommand(payment.memberId(), payment.pgTransactionKey())
            );
        }
    }
}

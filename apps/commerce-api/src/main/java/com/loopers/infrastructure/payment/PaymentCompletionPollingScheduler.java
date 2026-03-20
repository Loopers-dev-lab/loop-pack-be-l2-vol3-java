package com.loopers.infrastructure.payment;

import com.loopers.application.payment.PaymentUseCase;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.ZonedDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "loopers.payment.completion.polling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentCompletionPollingScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentUseCase paymentUseCase;

    @Value("${loopers.payment.completion.polling.requested-min-age-ms:15000}")
    private long requestedMinAgeMs;

    @Scheduled(fixedDelayString = "${loopers.payment.completion.polling.fixed-delay-ms:5000}")
    public void pollPendingPayments() {
        ZonedDateTime requestedCutoff = ZonedDateTime.now().minusNanos(requestedMinAgeMs * 1_000_000);
        List<Payment> candidates;
        try {
            candidates = paymentRepository.findAllByStatusIn(
                    List.of(PaymentStatus.REQUESTED, PaymentStatus.CANCEL_REQUESTED, PaymentStatus.CANCEL_RECONCILE_REQUIRED)
            );
        } catch (Exception e) {
            log.warn("Payment polling skipped due to repository error.", e);
            return;
        }

        for (Payment payment : candidates) {
            if (payment.status() == PaymentStatus.REQUESTED
                    && payment.updatedAt() != null
                    && payment.updatedAt().isAfter(requestedCutoff)) {
                continue;
            }
            try {
                paymentUseCase.reconcile(payment.memberId(), payment.orderId());
            } catch (CoreException e) {
                log.warn("Payment polling reconcile skipped. orderId={}, memberId={}, status={}, errorType={}, message={}",
                        payment.orderId(),
                        payment.memberId(),
                        payment.status(),
                        e.getErrorType(),
                        e.getCustomMessage() != null ? e.getCustomMessage() : e.getMessage());
            } catch (Exception e) {
                log.warn("Payment polling reconcile skipped due to unexpected error. orderId={}, memberId={}, status={}",
                        payment.orderId(), payment.memberId(), payment.status(), e);
            }
        }
    }
}

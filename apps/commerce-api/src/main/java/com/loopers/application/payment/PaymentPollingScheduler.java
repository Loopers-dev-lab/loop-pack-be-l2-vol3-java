package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentPollingScheduler {

    private static final int FAIL_FAST_THRESHOLD = 3;

    private final PaymentService paymentService;
    private final PaymentFacade paymentFacade;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void pollPendingPayments() {
        List<PaymentModel> pendingPayments = paymentService.getPendingPayments();
        if (pendingPayments.isEmpty()) {
            return;
        }

        log.info("PENDING 결제 {}건 폴링 시작", pendingPayments.size());

        int consecutiveFailures = 0;
        int recovered = 0;

        for (PaymentModel payment : pendingPayments) {
            try {
                paymentFacade.syncPaymentStatus(payment.getId());
                consecutiveFailures = 0;
                recovered++;
            } catch (Exception e) {
                consecutiveFailures++;
                log.warn("결제 상태 동기화 실패 - paymentId: {}, 연속 실패: {}/{}, reason: {}",
                    payment.getId(), consecutiveFailures, FAIL_FAST_THRESHOLD, e.getMessage());

                if (consecutiveFailures >= FAIL_FAST_THRESHOLD) {
                    log.error("PG 연속 {}건 실패 — 폴링 사이클 조기 종료 (남은 {}건은 다음 사이클에서 처리)",
                        FAIL_FAST_THRESHOLD, pendingPayments.size() - recovered - consecutiveFailures);
                    break;
                }
            }
        }

        log.info("PENDING 결제 폴링 완료 - 처리: {}/{}", recovered, pendingPayments.size());
    }
}

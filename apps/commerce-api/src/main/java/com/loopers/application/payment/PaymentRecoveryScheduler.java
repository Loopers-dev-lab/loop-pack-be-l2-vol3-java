package com.loopers.application.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRecoveryScheduler {

    private final PaymentFacade paymentFacade;

    @Scheduled(fixedDelayString = "${payment.recovery.fixed-delay-ms:5000}")
    @SchedulerLock(name = "recoverPendingPayments", lockAtLeastFor = "PT3S")
    public void recoverPendingPayments() {
        int recovered = paymentFacade.recoverPendingPayments();
        if (recovered > 0) {
            log.info("결제 복구 동기화 완료. recovered={}", recovered);
        }
    }
}

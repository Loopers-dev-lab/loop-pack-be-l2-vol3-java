package com.loopers.interfaces.scheduler;

import com.loopers.application.payment.PaymentReconciliationApp;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class PaymentReconciliationScheduler {

    private final PaymentReconciliationApp paymentReconciliationApp;

    @Value("${payment.reconciliation.pending-threshold-seconds}")
    private long pendingThresholdSeconds;

    // fixedDelay: 이전 실행 종료 후 얼마 뒤에 다음 실행 시작할 건지
    @Scheduled(fixedDelayString = "${payment.reconciliation.interval-ms}")
    public void reconcile() {
        ZonedDateTime threshold = ZonedDateTime.now().minusSeconds(pendingThresholdSeconds);
        paymentReconciliationApp.reconcile(threshold);
    }
}

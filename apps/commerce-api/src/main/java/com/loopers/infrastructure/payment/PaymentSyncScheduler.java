package com.loopers.infrastructure.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSyncScheduler {
    private final PaymentFacade paymentFacade;

    @Scheduled(fixedDelayString = "${payment.sync.interval-ms:300000}")
    public void syncPendingPayments() {
        log.info("PENDING 결제 자동 동기화 시작");
        List<PaymentInfo> results = paymentFacade.syncPendingPayments();
        log.info("PENDING 결제 자동 동기화 완료: {}건 처리", results.size());
    }
}

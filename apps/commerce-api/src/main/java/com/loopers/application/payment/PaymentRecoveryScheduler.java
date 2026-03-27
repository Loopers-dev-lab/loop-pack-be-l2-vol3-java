package com.loopers.application.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentRecoveryScheduler {

    private final PaymentFacade paymentFacade;

    /**
     * 5분마다 정체된 결제(PENDING/TIMED_OUT) 상태를 PG에 확인하여 복구
     */
    @Scheduled(fixedDelay = 300_000)
    public void recoverStalledPayments() {
        log.info("정체된 결제 복구 스케줄러 시작");
        paymentFacade.recoverStalledPayments();
        log.info("정체된 결제 복구 스케줄러 완료");
    }
}

package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private final PaymentFacade paymentFacade;

    @Scheduled(fixedDelay = 60000)
    public void recoverPendingPayments() {
        log.info("결제 복구 스케줄러 실행");
        paymentFacade.recoverPendingPayments();
    }
}

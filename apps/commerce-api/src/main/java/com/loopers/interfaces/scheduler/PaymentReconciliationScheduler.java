package com.loopers.interfaces.scheduler;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentService;
import com.loopers.domain.payment.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private final PaymentService paymentService;
    private final PaymentFacade paymentFacade;

    @Scheduled(fixedDelayString = "${payment.reconciliation.pending.interval-ms:60000}")
    public void reconcilePendingPayments() {
        List<Payment> pendings = paymentService.findRequestedOlderThan(
                ZonedDateTime.now().minusMinutes(2));
        if (pendings.isEmpty()) return;

        log.info("미결 결제 보정 대상 {}건 탐지", pendings.size());

        for (Payment payment : pendings) {
            try {
                paymentFacade.reconcilePending(payment.getId());
                log.info("미결 결제 보정 완료: paymentId={}", payment.getId());
            } catch (Exception e) {
                log.warn("미결 결제 보정 처리 실패: paymentId={}", payment.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${payment.reconciliation.cancel.interval-ms:60000}")
    public void reconcileCancelRequestedPayments() {
        List<Payment> cancelRequested = paymentService.findCancelRequestedOlderThan(
                ZonedDateTime.now().minusMinutes(2));
        if (cancelRequested.isEmpty()) return;

        log.info("취소 미완료 결제 보정 대상 {}건 탐지", cancelRequested.size());

        for (Payment payment : cancelRequested) {
            try {
                paymentFacade.reconcileCancel(payment.getId());
                log.info("취소 미완료 결제 보정 완료: paymentId={}", payment.getId());
            } catch (Exception e) {
                log.warn("취소 미완료 결제 보정 처리 실패: paymentId={}", payment.getId(), e);
            }
        }
    }
}

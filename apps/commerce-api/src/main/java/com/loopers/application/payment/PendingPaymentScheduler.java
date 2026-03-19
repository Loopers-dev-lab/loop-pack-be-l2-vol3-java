package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.gateway.PaymentQueryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingPaymentScheduler {

    private final PaymentService paymentService;
    private final PaymentGatewayExecutor gatewayExecutor;
    private final PaymentProcessor processor;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${payment.reconciliation.pending.interval-ms:60000}")
    public void reconcilePendingPayments() {
        List<Payment> pendings = paymentService.findRequestedOlderThan(
                ZonedDateTime.now().minusMinutes(2));
        if (pendings.isEmpty()) return;

        log.info("미결 결제 보정 대상 {}건 탐지", pendings.size());

        for (Payment payment : pendings) {
            reconcile(payment);
        }
    }

    private void reconcile(Payment payment) {
        try {
            PaymentQueryResult result = gatewayExecutor.query(payment);

            if (result.found() && result.done()) {
                transactionTemplate.executeWithoutResult(status ->
                        paymentService.markSucceeded(payment.getId()));
                log.info("미결 결제 보정 성공: paymentId={}", payment.getId());
            } else {
                transactionTemplate.executeWithoutResult(status ->
                        processor.failAndCompensate(payment.getId(), payment.getOrderId(), "PG 확인 불가 — 자동 만료"));
                log.info("미결 결제 보정 실패 처리: paymentId={}", payment.getId());
            }
        } catch (Exception e) {
            log.warn("미결 결제 보정 처리 실패: paymentId={}", payment.getId(), e);
        }
    }
}

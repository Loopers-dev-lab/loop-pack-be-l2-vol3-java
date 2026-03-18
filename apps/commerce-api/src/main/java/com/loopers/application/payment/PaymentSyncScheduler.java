package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSyncScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentApp paymentApp;

    @Value("${payment.scheduler.stale-requested-minutes:10}")
    private int staleRequestedMinutes;

    @Value("${payment.scheduler.cb-fast-fail-expiry-minutes:10}")
    private int cbFastFailExpiryMinutes;

    @Scheduled(fixedDelayString = "${payment.scheduler.sync-interval-ms:60000}")
    @SchedulerLock(name = "payment_sync_requested", lockAtMostFor = "PT3M", lockAtLeastFor = "PT1M")
    public void syncStaleRequestedPayments() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusMinutes(staleRequestedMinutes);
        List<PaymentModel> staleList = paymentRepository.findStaleRequested(cutoff);
        for (PaymentModel payment : staleList) {
            syncOne(payment);
        }
    }

    @Scheduled(fixedDelayString = "${payment.scheduler.sync-interval-ms:60000}")
    @SchedulerLock(name = "payment_expire_cb_fast_fail", lockAtMostFor = "PT3M", lockAtLeastFor = "PT1M")
    public void expireCbFastFailPendingPayments() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusMinutes(cbFastFailExpiryMinutes);
        List<PaymentModel> expiredList = paymentRepository.findExpiredCbFastFail(cutoff);
        for (PaymentModel payment : expiredList) {
            expireOne(payment);
        }
    }

    private void syncOne(PaymentModel payment) {
        try {
            paymentApp.syncFromGateway(payment.getId(), payment.getRefMemberId());
        } catch (OptimisticLockingFailureException e) {
            handleOptimisticLockConflict(payment, "sync");
        } catch (Exception e) {
            log.error("결제 sync 실패. paymentId={}", payment.getId(), e);
        }
    }

    private void expireOne(PaymentModel payment) {
        try {
            paymentApp.forceFailPayment(payment.getId());
        } catch (OptimisticLockingFailureException e) {
            handleOptimisticLockConflict(payment, "expire");
        } catch (Exception e) {
            log.error("CB fast-fail 만료 처리 실패. paymentId={}", payment.getId(), e);
        }
    }

    private void handleOptimisticLockConflict(PaymentModel payment, String operation) {
        PaymentModel reloaded = paymentRepository.findById(payment.getId()).orElse(null);
        if (reloaded == null) {
            log.warn("[{}] 결제 재조회 실패 (삭제됨?). paymentId={}", operation, payment.getId());
            return;
        }
        if (reloaded.getStatus() == PaymentStatus.COMPLETED || reloaded.getStatus() == PaymentStatus.FAILED) {
            log.info("[{}] 낙관적 락 충돌 - 이미 확정 상태. paymentId={}, status={}", operation, reloaded.getId(), reloaded.getStatus());
            return;
        }
        log.warn("[{}] 낙관적 락 충돌 - 아직 미확정 상태. paymentId={}, status={}", operation, reloaded.getId(), reloaded.getStatus());
    }
}

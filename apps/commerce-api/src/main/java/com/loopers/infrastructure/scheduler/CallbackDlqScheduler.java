package com.loopers.infrastructure.scheduler;

import com.loopers.application.payment.PaymentRecoveryService;
import com.loopers.domain.payment.CallbackInbox;
import com.loopers.domain.payment.CallbackInboxRepository;
import com.loopers.domain.payment.CallbackInboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Callback DLQ 재처리 스케줄러.
 *
 * <p>RECEIVED 상태인 콜백 중 30초 이상 미처리된 건을 재처리.
 * 최대 재시도 3회 초과 시 FAILED 처리.</p>
 *
 * @see <a href="05-payment-resilience.md §8.5">Callback Inbox DLQ</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackDlqScheduler {

    private static final int THRESHOLD_SECONDS = 30;
    private static final int MAX_RETRY = 3;

    private final CallbackInboxRepository callbackInboxRepository;
    private final PaymentRecoveryService paymentRecoveryService;

    @Scheduled(fixedRate = 30_000)
    public void reprocessFailedCallbacks() {
        List<CallbackInbox> receivedInboxes = callbackInboxRepository.findAllByStatus(CallbackInboxStatus.RECEIVED);
        ZonedDateTime threshold = ZonedDateTime.now().minusSeconds(THRESHOLD_SECONDS);

        for (CallbackInbox inbox : receivedInboxes) {
            if (inbox.getCreatedAt() != null && inbox.getCreatedAt().isBefore(threshold)) {
                reprocessCallback(inbox);
            }
        }
    }

    private void reprocessCallback(CallbackInbox inbox) {
        if (inbox.getRetryCount() >= MAX_RETRY) {
            inbox.markFailed("최대 재시도 횟수 초과");
            callbackInboxRepository.save(inbox);
            log.error("DLQ 최대 재시도 초과 — FAILED: inboxId={}, transactionKey={}",
                inbox.getId(), inbox.getTransactionKey());
            return;
        }

        try {
            paymentRecoveryService.processCallback(
                inbox.getTransactionKey(), inbox.getPgStatus(), inbox.getPayload());
            log.info("DLQ 재처리 성공: inboxId={}, transactionKey={}",
                inbox.getId(), inbox.getTransactionKey());
        } catch (Exception e) {
            inbox.markFailed(e.getMessage());
            callbackInboxRepository.save(inbox);
            log.warn("DLQ 재처리 실패: inboxId={}, error={}", inbox.getId(), e.getMessage());
        }
    }
}

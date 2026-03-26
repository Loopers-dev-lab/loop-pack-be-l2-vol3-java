package com.loopers.infrastructure.scheduler;

import com.loopers.domain.payment.*;
import com.loopers.infrastructure.pg.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbox 폴러 — 5초 주기.
 *
 * <p>TX-1에서 저장된 Outbox(PENDING)를 감지하여 PG 호출 누락을 수 초 내 재시도.
 * 배치 복구(1분 주기)보다 빠른 1차 복구 경로.</p>
 *
 * <p>폴러 동작:</p>
 * <ol>
 *   <li>Outbox(PENDING) 전건 조회</li>
 *   <li>Payment 현재 상태 확인 → 이미 PAID/FAILED면 Outbox PROCESSED</li>
 *   <li>PG 상태 확인 (GET /payments?orderId=xxx) → 기록 있으면 Outbox PROCESSED</li>
 *   <li>PG 기록 없음 → PG 결제 요청(POST) 실행</li>
 *   <li>retry_count 증가, 최대 3회 초과 → FAILED + 운영 알림</li>
 * </ol>
 *
 * @see <a href="05-payment-resilience.md §13.4">Outbox 폴러</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPollerScheduler {

    private static final int MAX_RETRY = 3;

    private final PaymentOutboxRepository outboxRepository;
    private final PaymentRepository paymentRepository;
    private final PgRouter pgRouter;

    @Scheduled(fixedRate = 5_000)
    public void pollOutbox() {
        List<PaymentOutbox> pendingOutboxes = outboxRepository.findAllByStatus(PaymentOutboxStatus.PENDING);

        for (PaymentOutbox outbox : pendingOutboxes) {
            try {
                processOutbox(outbox);
            } catch (Exception e) {
                log.error("Outbox 처리 실패: outboxId={}, error={}", outbox.getId(), e.getMessage());
                outbox.incrementRetry();
                if (outbox.getRetryCount() > MAX_RETRY) {
                    outbox.markFailed();
                    log.error("Outbox 최대 재시도 초과 — FAILED: outboxId={}, paymentId={}",
                        outbox.getId(), outbox.getPaymentId());
                }
                outboxRepository.save(outbox);
            }
        }
    }

    private void processOutbox(PaymentOutbox outbox) {
        // 1. Payment 현재 상태 확인
        PaymentModel payment = paymentRepository.findById(outbox.getPaymentId()).orElse(null);
        if (payment == null) {
            outbox.markFailed();
            outboxRepository.save(outbox);
            log.warn("Outbox — Payment 없음: outboxId={}, paymentId={}", outbox.getId(), outbox.getPaymentId());
            return;
        }

        // 이미 최종 상태 → Outbox PROCESSED
        if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.FAILED) {
            outbox.markProcessed();
            outboxRepository.save(outbox);
            log.info("Outbox — 이미 해결됨: outboxId={}, paymentStatus={}", outbox.getId(), payment.getStatus());
            return;
        }

        // 이미 PENDING (PG 호출 완료) → Outbox PROCESSED
        if (payment.getStatus() == PaymentStatus.PENDING) {
            outbox.markProcessed();
            outboxRepository.save(outbox);
            log.info("Outbox — PG 호출 완료: outboxId={}, paymentStatus=PENDING", outbox.getId());
            return;
        }

        // 2. PG 상태 확인 (orderId로 조회 — 멱등성 보장)
        try {
            PgPaymentStatusResponse pgStatus = pgRouter.getPaymentByOrderId(
                String.valueOf(outbox.getOrderId()),
                pgRouter.getPrimaryClient().getProviderName());

            if (pgStatus != null && pgStatus.transactionKey() != null) {
                // PG에 기록 존재 → Payment 상태 업데이트 + Outbox PROCESSED
                payment.markPending(pgStatus.transactionKey(),
                    pgRouter.getPrimaryClient().getProviderName());
                paymentRepository.save(payment);
                outbox.markProcessed();
                outboxRepository.save(outbox);
                log.info("Outbox — PG 기록 발견: outboxId={}, transactionKey={}",
                    outbox.getId(), pgStatus.transactionKey());
                return;
            }
        } catch (Exception e) {
            log.debug("PG 상태 확인 실패 — PG 호출 진행: outboxId={}", outbox.getId());
        }

        // 3. PG 기록 없음 → PG 결제 요청
        PgPaymentRequest pgRequest = PgPaymentRequest.of(
            outbox.getOrderId(), payment.getCardType(), payment.getCardNo(),
            payment.getAmount(), "http://localhost:8080/api/v1/payments/callback");

        PgPaymentResponse pgResponse = pgRouter.requestPayment(pgRequest);
        payment.markPending(pgResponse.transactionKey(), pgResponse.pgProvider());
        paymentRepository.save(payment);
        outbox.markProcessed();
        outboxRepository.save(outbox);

        log.info("Outbox — PG 결제 요청 완료: outboxId={}, transactionKey={}, provider={}",
            outbox.getId(), pgResponse.transactionKey(), pgResponse.pgProvider());
    }
}

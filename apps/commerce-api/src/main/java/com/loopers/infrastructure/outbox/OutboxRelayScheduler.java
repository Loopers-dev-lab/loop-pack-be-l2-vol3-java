package com.loopers.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outbox Relay: 미발행 이벤트를 폴링하여 Kafka로 전송한다.
 *
 * 동작 흐름:
 * 1. 미발행 + 미실패 이벤트를 최대 100건 조회
 * 2. 개별 TX에서 FOR UPDATE 잠금 → send().get() 동기 전송 → markPublished()
 * 3. 전송 실패 시 retryCount 증가, 한도(5회) 초과 시 markFailed()
 * 4. 연속 3건 실패 시 사이클 조기 종료 (Kafka 장애 시 불필요한 반복 방지)
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxRelayScheduler {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final PlatformTransactionManager transactionManager;

    private static final long SEND_TIMEOUT_SECONDS = 5;
    private static final int MAX_PUBLISH_RETRY = 5;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        List<OutboxEvent> events = outboxEventJpaRepository
                .findTop100ByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAsc();
        if (events.isEmpty()) return;

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        int publishedCount = 0;
        int failedCount = 0;
        int consecutiveFailures = 0;

        for (OutboxEvent event : events) {
            try {
                Boolean success = txTemplate.execute(status -> {
                    OutboxEvent locked = outboxEventJpaRepository.findByIdForUpdate(event.getId())
                            .orElse(null);
                    if (locked == null || locked.getPublishedAt() != null) return null;

                    try {
                        OutboxMessage message = new OutboxMessage(
                                locked.getId(), locked.getAggregateType(), locked.getAggregateId(),
                                locked.getEventType(), locked.getPayload());
                        kafkaTemplate.send(locked.getTopic(), locked.getMessageKey(), message)
                                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        locked.markPublished();
                        return true;
                    } catch (Exception e) {
                        locked.incrementRetryCount();
                        if (locked.isRetryExhausted(MAX_PUBLISH_RETRY)) {
                            locked.markFailed();
                            log.error("[Outbox Relay] 최종 실패 — 수동 확인 필요: eventId={}, topic={}",
                                    locked.getId(), locked.getTopic());
                        } else {
                            log.warn("[Outbox Relay] 발행 실패: eventId={}, topic={}, retryCount={}, error={}",
                                    locked.getId(), locked.getTopic(), locked.getRetryCount(), e.getMessage());
                        }
                        return false;
                    }
                });

                if (success == null) continue;
                if (Boolean.TRUE.equals(success)) {
                    publishedCount++;
                    consecutiveFailures = 0;
                } else {
                    failedCount++;
                    consecutiveFailures++;
                }
            } catch (Exception e) {
                failedCount++;
                consecutiveFailures++;
                log.error("[Outbox Relay] TX 실패: eventId={}, error={}", event.getId(), e.getMessage());
            }

            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                log.warn("[Outbox Relay] {}건 연속 실패 — 이번 사이클 조기 종료", consecutiveFailures);
                break;
            }
        }

        if (publishedCount > 0 || failedCount > 0) {
            log.info("[Outbox Relay] {}건 발행 완료, {}건 실패", publishedCount, failedCount);
        }
    }
}

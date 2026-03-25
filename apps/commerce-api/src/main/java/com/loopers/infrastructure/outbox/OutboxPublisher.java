package com.loopers.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@Component
public class OutboxPublisher {

    private final OutboxJpaRepository outboxJpaRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final PlatformTransactionManager transactionManager;

    private static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 5;
    private static final int MAX_PUBLISH_RETRY = 5;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        List<Long> pendingIds = outboxJpaRepository.findPendingIds(BATCH_SIZE);
        if (pendingIds.isEmpty()) return;

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        int publishedCount = 0;
        int failedCount = 0;
        int consecutiveFailures = 0;

        for (Long id : pendingIds) {
            try {
                Boolean success = txTemplate.execute(status -> {
                    OutboxEvent event = outboxJpaRepository.findByIdForUpdate(id).orElse(null);
                    if (event == null) return null;

                    try {
                        kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                            .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        event.markPublished();
                        return true;
                    } catch (Exception e) {
                        event.incrementRetryCount();
                        if (event.isRetryExhausted(MAX_PUBLISH_RETRY)) {
                            event.markFailed();
                            log.error("[Outbox 최종 실패] eventId={}, topic={} — 재시도 한도 초과. 수동 확인 필요.",
                                event.getEventId(), event.getTopic());
                        } else {
                            log.error("[Outbox 발행 실패] eventId={}, topic={}, retryCount={}, error={}",
                                event.getEventId(), event.getTopic(), event.getRetryCount(), e.getMessage());
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
                log.error("[Outbox TX 실패] id={}, error={}", id, e.getMessage());
            }

            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                log.warn("[Outbox] {}건 연속 실패 — 이번 사이클 조기 종료. 다음 사이클에서 재시도.", consecutiveFailures);
                break;
            }
        }

        if (publishedCount > 0 || failedCount > 0) {
            log.info("[Outbox] {}건 발행 완료, {}건 실패", publishedCount, failedCount);
        }
    }
}

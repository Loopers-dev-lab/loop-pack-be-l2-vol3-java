package com.loopers.interfaces.scheduler;

import com.loopers.support.outbox.OutboxEvent;
import com.loopers.support.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "scheduler.outbox.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY_COUNT = 5;
    private static final long MAX_AGE_MINUTES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPending(BATCH_SIZE);
        if (pendingEvents.isEmpty()) {
            return;
        }

        int successCount = 0;
        int failedCount = 0;

        for (OutboxEvent event : pendingEvents) {
            if (event.isExpired(MAX_AGE_MINUTES)) {
                event.markFailed();
                outboxEventRepository.save(event);
                failedCount++;
                log.error("Outbox 이벤트 만료 → FAILED: eventId={}, eventType={}", event.getEventId(), event.getEventType());
                continue;
            }

            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload()).get();
                event.markSent();
                outboxEventRepository.save(event);
                successCount++;
            } catch (Exception e) {
                event.incrementRetryCount();
                if (event.getRetryCount() >= MAX_RETRY_COUNT) {
                    event.markFailed();
                    log.error("Outbox 재시도 초과 → FAILED: eventId={}, retryCount={}", event.getEventId(), event.getRetryCount(), e);
                } else {
                    log.warn("Outbox 발행 실패, 재시도 예정: eventId={}, retryCount={}", event.getEventId(), event.getRetryCount(), e);
                }
                outboxEventRepository.save(event);
                failedCount++;
            }
        }

        if (successCount > 0 || failedCount > 0) {
            log.info("Outbox relay 완료: success={}, failed={}", successCount, failedCount);
        }
    }
}

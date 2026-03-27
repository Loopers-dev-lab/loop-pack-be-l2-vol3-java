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

/**
 * Outbox 보완 Relay — 즉시 발행(afterCommit)이 실패한 이벤트만 수거.
 *
 * 메인 발행: OutboxEventService의 afterCommit 비동기 send (99.x%)
 * 보완 발행: 이 스케줄러가 stale PENDING 수거 (0.x%)
 *
 * Exponential Backoff: 1분 → 2분 → 4분 → 8분 → 16분 (최대 30분 cap)
 * 최대 5회 재시도 후 FAILED → 운영자 개입.
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "scheduler.outbox.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int BATCH_SIZE = 200;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void compensatePendingEvents() {
        List<OutboxEvent> retryableEvents = outboxEventRepository.findRetryableEvents(BATCH_SIZE);
        if (retryableEvents.isEmpty()) {
            return;
        }

        int retryCount = 0;
        int deadCount = 0;

        for (OutboxEvent event : retryableEvents) {
            if (event.isMaxRetriesExceeded()) {
                event.markFailed();
                outboxEventRepository.save(event);
                deadCount++;
                log.error("Outbox 최대 재시도 초과 → FAILED: eventId={}, retryCount={}",
                        event.getEventId(), event.getRetryCount());
                continue;
            }

            kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("보완 발행 실패: eventId={}, retry={}/{}",
                                    event.getEventId(), event.getRetryCount(), event.getMaxRetries(), ex);
                        }
                    });

            event.scheduleNextRetry();
            outboxEventRepository.save(event);
            retryCount++;
        }

        if (retryCount > 0 || deadCount > 0) {
            log.info("Outbox 보완 relay: 재시도={}, FAILED={}", retryCount, deadCount);
        }
    }
}

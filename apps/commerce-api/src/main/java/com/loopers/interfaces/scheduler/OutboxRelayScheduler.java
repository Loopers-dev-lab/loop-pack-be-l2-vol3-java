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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 보완 Relay — 즉시 발행(afterCommit)이 실패한 이벤트만 수거.
 *
 * 메인 발행: OutboxEventService의 afterCommit 비동기 send (99.x%)
 * 보완 발행: 이 스케줄러가 stale PENDING 수거 (0.x%)
 *
 * .get(5초)으로 ACK 대기 → 성공 시 SENT(Consumer 셀프컨슘이 최종 SENT).
 * 실패 시 retryCount 증가 → 10회 초과 시 FAILED → 운영자 개입.
 * 스케줄러는 사용자 대기 없으므로 동기 블로킹이 안전.
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "scheduler.outbox.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int BATCH_SIZE = 200;
    private static final int MAX_RETRY_COUNT = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 60000)
    public void compensatePendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPending(BATCH_SIZE);
        if (pendingEvents.isEmpty()) {
            return;
        }

        int sentCount = 0;
        int failCount = 0;

        for (OutboxEvent event : pendingEvents) {
            try {
                Map<String, Object> envelope = Map.of(
                        "eventId", event.getEventId(),
                        "eventType", event.getEventType(),
                        "payload", event.getPayload()
                );
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), envelope)
                        .get(5, TimeUnit.SECONDS);

                event.markSent();
                outboxEventRepository.save(event);
                sentCount++;
            } catch (Exception e) {
                event.incrementRetryCount();
                if (event.getRetryCount() >= MAX_RETRY_COUNT) {
                    event.markFailed();
                    log.error("Outbox FAILED: eventId={}, retryCount={}", event.getEventId(), event.getRetryCount(), e);
                }
                outboxEventRepository.save(event);
                failCount++;
            }
        }

        if (sentCount > 0 || failCount > 0) {
            log.info("Outbox 보완 relay: sent={}, failed={}", sentCount, failCount);
        }
    }
}

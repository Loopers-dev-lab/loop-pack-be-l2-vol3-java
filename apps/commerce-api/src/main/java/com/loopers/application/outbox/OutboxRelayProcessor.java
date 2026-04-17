package com.loopers.application.outbox;

import com.loopers.support.outbox.OutboxEvent;
import com.loopers.support.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayProcessor {

    private static final int MAX_RETRY_COUNT = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Transactional
    public void relayPendingEvents(int batchSize) {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPending(batchSize);
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
                sentCount++;
            } catch (Exception e) {
                event.incrementRetryCount();
                if (event.getRetryCount() >= MAX_RETRY_COUNT) {
                    event.markFailed();
                    log.error("Outbox FAILED: eventId={}, retryCount={}", event.getEventId(), event.getRetryCount(), e);
                }
                failCount++;
            }
        }

        if (sentCount > 0 || failCount > 0) {
            log.info("Outbox 보완 relay: sent={}, failed={}", sentCount, failCount);
        }
    }
}

package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox 테이블의 PENDING 이벤트를 Kafka로 발행하는 Publisher.
 * 주기적으로 폴링하여 이벤트를 발행하고 상태를 갱신.
 * KafkaTemplate 빈이 있을 때만 활성화.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(KafkaTemplate.class)
public class OutboxPublisher {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, EventEnvelope> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(BATCH_SIZE);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Publishing {} pending outbox events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            publishEvent(event);
        }
    }

    private void publishEvent(OutboxEvent event) {
        try {
            EventEnvelope envelope = new EventEnvelope(
                event.getEventId(),
                event.getEventType(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getCreatedAt(),
                event.getPayload()
            );

            ProducerRecord<String, EventEnvelope> record = new ProducerRecord<>(
                event.getTopic(),
                event.getAggregateId(),
                envelope
            );

            kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        outboxEventRepository.markAsSent(event.getId());
                        log.debug("Event published successfully: eventId={}, topic={}",
                            event.getEventId(), event.getTopic());
                    } else {
                        outboxEventRepository.incrementRetryCount(event.getId());
                        log.error("Failed to publish event: eventId={}, topic={}, error={}",
                            event.getEventId(), event.getTopic(), ex.getMessage());
                    }
                });

        } catch (Exception e) {
            outboxEventRepository.incrementRetryCount(event.getId());
            log.error("Error creating event envelope: eventId={}", event.getEventId(), e);
        }
    }
}

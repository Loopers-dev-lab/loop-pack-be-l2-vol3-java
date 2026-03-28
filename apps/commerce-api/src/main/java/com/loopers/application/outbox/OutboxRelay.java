package com.loopers.application.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findUnpublishedEvents();

        for (OutboxEvent event : events) {
            try {
                String topic = resolveTopicName(event.getAggregateType());
                String key = String.valueOf(event.getAggregateId());

                // eventId를 포함한 메시지 구조로 발행 — Consumer가 멱등 처리에 사용
                Map<String, Object> message = Map.of(
                    "eventId", event.getEventId(),
                    "eventType", event.getEventType(),
                    "aggregateType", event.getAggregateType(),
                    "aggregateId", event.getAggregateId(),
                    "payload", event.getPayload(),
                    "createdAt", event.getCreatedAt().toString()
                );

                kafkaTemplate.send(topic, key, message).get(5, TimeUnit.SECONDS);
                event.markPublished();

                log.info("Outbox 이벤트 발행: topic={}, key={}, eventId={}",
                    topic, key, event.getEventId());
            } catch (Exception e) {
                log.error("Outbox 이벤트 발행 실패: id={}", event.getId(), e);
                break;
            }
        }
    }
    private String resolveTopicName(String aggregateType) {
        return switch (aggregateType) {
            case "ORDER" -> "order-events";
            case "PRODUCT" -> "catalog-events";
            case "COUPON" -> "coupon-issue-requests";
            default -> throw new IllegalArgumentException("Unknown aggregate: " + aggregateType);
        };
    }
}

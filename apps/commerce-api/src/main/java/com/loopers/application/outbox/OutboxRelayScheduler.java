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

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final String CATALOG_EVENTS_TOPIC = "catalog.events.topic-v1";
    private static final String ORDER_EVENTS_TOPIC = "order.events.topic-v1";
    private static final String COUPON_ISSUE_TOPIC = "coupon.issue-requests.topic-v1";
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> events = outboxEventRepository.findUnpublished(BATCH_SIZE);
        if (events.isEmpty()) {
            return;
        }

        for (OutboxEvent event : events) {
            try {
                String topic = resolveTopic(event.getAggregateType());
                String key = String.valueOf(event.getAggregateId());
                kafkaTemplate.send(topic, key, event.getPayload()).get();
                event.markPublished();
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.warn("Outbox 릴레이 실패 - id: {}, eventType: {}", event.getId(), event.getEventType(), e);
            }
        }

        log.info("Outbox 릴레이 완료 - {}건 처리", events.size());
    }

    private String resolveTopic(String aggregateType) {
        return switch (aggregateType) {
            case "PRODUCT" -> CATALOG_EVENTS_TOPIC;
            case "ORDER" -> ORDER_EVENTS_TOPIC;
            case "COUPON" -> COUPON_ISSUE_TOPIC;
            default -> throw new IllegalArgumentException("Unknown aggregate type: " + aggregateType);
        };
    }
}

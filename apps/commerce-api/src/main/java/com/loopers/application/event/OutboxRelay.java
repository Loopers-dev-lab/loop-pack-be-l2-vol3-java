package com.loopers.application.event;

import com.loopers.domain.outbox.model.OutboxEvent;
import com.loopers.domain.outbox.model.OutboxEventType;
import com.loopers.domain.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        List<OutboxEvent> events = outboxEventRepository.findByStatusInit();
        if (events.isEmpty()) {
            return;
        }

        for (OutboxEvent event : events) {
            String topic = resolveTopic(event.getEventType());
            try {
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload()).get();
                event.markPublished();
                log.info("Outbox 이벤트 발행 - topic: {}, key: {}, eventType: {}", topic, event.getAggregateId(), event.getEventType());
            } catch (Exception e) {
                event.markFailed();
                log.error("Outbox 이벤트 발행 실패 - eventId: {}", event.getId(), e);
            }
            outboxEventRepository.save(event);
        }
    }

    private String resolveTopic(OutboxEventType eventType) {
        return switch (eventType) {
            case FAVORITE_ADDED, FAVORITE_REMOVED -> "catalog-events";
            case ORDER_CREATED, PAYMENT_COMPLETED -> "order-events";
        };
    }
}

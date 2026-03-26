package com.loopers.application.outbox;

import com.loopers.domain.outbox.CatalogEventMessage;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPollingScheduler {

    private static final int BATCH_SIZE = 50;

    private final OutboxEventService outboxEventService;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Transactional
    @Scheduled(fixedDelay = 5000)
    public void pollAndPublish() {
        List<OutboxEvent> events = outboxEventService.findPendingEvents(BATCH_SIZE);

        for (OutboxEvent event : events) {
            try {
                CatalogEventMessage message = CatalogEventMessage.from(event);
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), message).get();
                outboxEventService.markAsSent(event);
            } catch (Exception e) {
                log.warn("Outbox 이벤트 발행 실패: eventId={}", event.getEventId(), e);
            }
        }
    }
}

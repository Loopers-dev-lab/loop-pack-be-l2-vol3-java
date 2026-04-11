package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.ranking.RankingApp;
import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.infrastructure.kafka.StreamerKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private static final String TOPIC = "order-events";
    private static final String SUPPORTED_EVENT_TYPE = "OrderCreated";

    private final RankingApp rankingApp;
    private final EventHandledRepository eventHandledRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = TOPIC,
            groupId = "commerce-streamer-order",
            containerFactory = StreamerKafkaConfig.DLQ_BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<Object, Object> record = records.get(i);
            try {
                OrderEventPayload payload = parse(record);
                if (!SUPPORTED_EVENT_TYPE.equals(payload.eventType())) {
                    log.warn("[ORDER_EVENT] 미지원 eventType={}, offset={} — 건너뜀",
                            payload.eventType(), record.offset());
                    continue;
                }
                processIfNotHandled(payload);
            } catch (Exception e) {
                log.error("[ORDER_EVENT_FAILED] offset={}, key={}", record.offset(), record.key(), e);
                throw new BatchListenerFailedException(
                        "order-events processing failed at index " + i, e, i);
            }
        }
        acknowledgment.acknowledge();
    }

    @Transactional
    public void processIfNotHandled(OrderEventPayload payload) {
        if (eventHandledRepository.existsByEventId(payload.eventId())) {
            return;
        }
        java.time.LocalDate date = payload.createdAt().toLocalDate();
        for (OrderItemEventPayload item : payload.items()) {
            rankingApp.applyOrderScore(item.productDbId(), item.price(), item.quantity(), date);
        }
        eventHandledRepository.save(EventHandledModel.create(payload.eventId(), TOPIC));
    }

    private OrderEventPayload parse(ConsumerRecord<Object, Object> record) {
        try {
            return objectMapper.readValue((byte[]) record.value(), OrderEventPayload.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize OrderEventPayload", e);
        }
    }
}

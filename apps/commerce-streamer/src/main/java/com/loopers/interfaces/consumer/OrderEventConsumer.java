package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.infrastructure.kafka.StreamerKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private static final String TOPIC = "order-events";

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = TOPIC,
            groupId = "commerce-streamer-order",
            containerFactory = StreamerKafkaConfig.DLQ_BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            OrderEventPayload payload = parse(record);
            log.debug("Received order event: eventId={}, eventType={}", payload.eventId(), payload.eventType());
        }
        acknowledgment.acknowledge();
    }

    private OrderEventPayload parse(ConsumerRecord<Object, Object> record) {
        try {
            return objectMapper.readValue((byte[]) record.value(), OrderEventPayload.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize OrderEventPayload", e);
        }
    }
}

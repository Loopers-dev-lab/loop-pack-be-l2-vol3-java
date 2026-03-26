package com.loopers.application.metrics;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ProductMetricsAckPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String outboxAckTopic;

    public ProductMetricsAckPublisher(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @org.springframework.beans.factory.annotation.Value("${loopers.kafka.topic.outbox-ack:commerce.outbox.ack.v1}") String outboxAckTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxAckTopic = outboxAckTopic;
    }

    public void publish(UUID eventId, String consumerGroup) {
        kafkaTemplate.send(outboxAckTopic, eventId.toString(), new AckMessage(eventId, consumerGroup, Instant.now()));
    }

    public record AckMessage(
            UUID eventId,
            String consumerGroup,
            Instant ackedAt
    ) {
    }
}

package com.loopers.infrastructure.kafka;

import com.loopers.domain.outbox.OutboxModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public CompletableFuture<SendResult<Object, Object>> send(OutboxModel outbox) {
        return kafkaTemplate.send(outbox.getTopic(), outbox.getAggregateId(), outbox.getPayload());
    }
}

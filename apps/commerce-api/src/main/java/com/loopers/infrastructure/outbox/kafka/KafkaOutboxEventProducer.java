package com.loopers.infrastructure.outbox.kafka;

import java.util.function.Consumer;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventProducer;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KafkaOutboxEventProducer implements OutboxEventProducer {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Override
    public void produceEvent(OutboxEvent outboxEvent, Runnable onSuccess, Consumer<Throwable> onFailure) {
        kafkaTemplate.send(outboxEvent.getTopic(), outboxEvent.getPartitionKey(), outboxEvent.getPayload())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        onFailure.accept(ex);
                    } else {
                        onSuccess.run();
                    }
                });
    }
}

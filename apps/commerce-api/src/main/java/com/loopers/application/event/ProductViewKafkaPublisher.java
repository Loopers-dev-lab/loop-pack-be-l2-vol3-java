package com.loopers.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductViewKafkaPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(ProductViewedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "PRODUCT_VIEWED",
                "productId", event.productId(),
                "memberId", event.memberId()
            ));
            kafkaTemplate.send("catalog-events", String.valueOf(event.productId()), payload);
        } catch (JsonProcessingException e) {
            log.warn("조회수 이벤트 Kafka 발행 실패 — productId={}", event.productId(), e);
        }
    }
}

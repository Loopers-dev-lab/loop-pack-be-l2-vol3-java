package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.behavior.event.BehaviorActionType;
import com.loopers.application.behavior.event.BehaviorLoggedEvent;
import com.loopers.contract.kafka.ProductMetricsEventMessage;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZonedDateTime;

@Component
public class BehaviorOutboxEventHandler {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${loopers.kafka.topic.product-metrics:commerce.product.metrics.v1}")
    private String productMetricsTopic;

    public BehaviorOutboxEventHandler(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(BehaviorLoggedEvent event) {
        if (event.productId() == null || event.productId().isBlank()) {
            return;
        }

        long deltaLike = 0;
        long deltaSales = 0;
        long deltaView = 0;

        if (event.actionType() == BehaviorActionType.LIKE_REGISTER) {
            deltaLike = event.quantity();
        } else if (event.actionType() == BehaviorActionType.LIKE_CANCEL) {
            deltaLike = -event.quantity();
        } else if (event.actionType() == BehaviorActionType.PRODUCT_DETAIL_REQUESTED) {
            deltaView = event.quantity();
        }

        ProductMetricsEventMessage message = new ProductMetricsEventMessage(
                event.eventId(),
                event.actionType().name(),
                event.productId(),
                deltaLike,
                deltaSales,
                0,
                deltaView,
                event.version(),
                event.occurredAt()
        );
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "Outbox payload 직렬화에 실패했습니다.");
        }

        outboxEventRepository.save(OutboxEvent.pending(
                event.eventId(),
                event.actionType().name(),
                "product_metrics",
                event.productId(),
                productMetricsTopic,
                event.productId(),
                payloadJson,
                ZonedDateTime.now()
        ));
    }
}

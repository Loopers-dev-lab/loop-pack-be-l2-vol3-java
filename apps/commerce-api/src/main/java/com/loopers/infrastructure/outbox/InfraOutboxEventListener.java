package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loopers.domain.event.LikeToggledEvent;
import com.loopers.domain.event.OrderCanceledEvent;
import com.loopers.domain.event.OrderCreatedEvent;
import com.loopers.domain.event.PaymentCompletedEvent;
import com.loopers.domain.event.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class InfraOutboxEventListener {
    private final OutboxJpaRepository outboxJpaRepository;
    private final AsyncOutboxPublisher asyncOutboxPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        saveAndPublish("Order", String.valueOf(event.orderId()), "OrderCreated",
                "order-events", String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderCanceled(OrderCanceledEvent event) {
        saveAndPublish("Order", String.valueOf(event.orderId()), "OrderCanceled",
                "order-events", String.valueOf(event.orderId()), event);
    }

    @EventListener
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        saveAndPublish("Payment", String.valueOf(event.paymentId()), "PaymentCompleted",
                "order-events", String.valueOf(event.orderId()), event);
    }

    @EventListener
    public void handleProductViewed(ProductViewedEvent event) {
        saveAndPublish("Product", String.valueOf(event.productId()), "ProductViewed",
                "catalog-events", String.valueOf(event.productId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleLikeToggled(LikeToggledEvent event) {
        saveAndPublish("Product", String.valueOf(event.productId()), "LikeToggled",
                "catalog-events", String.valueOf(event.productId()), event);
    }

    private void saveAndPublish(String aggregateType, String aggregateId, String eventType,
                                String topic, String partitionKey, Object event) {
        String payload = toPayload(eventType, event);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            saveInCurrentTxAndPublish(aggregateType, aggregateId, eventType, topic, partitionKey, payload);
        } else {
            saveInNewTxAndPublish(aggregateType, aggregateId, eventType, topic, partitionKey, payload);
        }
    }

    private void saveInCurrentTxAndPublish(String aggregateType, String aggregateId, String eventType,
                                           String topic, String partitionKey, String payload) {
        Outbox outbox = Outbox.create(aggregateType, aggregateId, eventType, topic, partitionKey, payload);
        outboxJpaRepository.save(outbox);
        log.info("Outbox 저장: {} {}={}", eventType, aggregateType.toLowerCase() + "Id", aggregateId);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    asyncOutboxPublisher.publishAsync(outbox.getId(), topic, partitionKey, payload);
                } catch (RejectedExecutionException e) {
                    log.warn("asyncExecutor 포화로 즉시 발행 건너뜀, relay 스케줄러가 재시도: outboxId={}", outbox.getId());
                }
            }
        });
    }

    private void saveInNewTxAndPublish(String aggregateType, String aggregateId, String eventType,
                                       String topic, String partitionKey, String payload) {
        Outbox outbox = transactionTemplate.execute(status -> {
            Outbox o = Outbox.create(aggregateType, aggregateId, eventType, topic, partitionKey, payload);
            return outboxJpaRepository.save(o);
        });
        log.info("Outbox 저장: {} {}={}", eventType, aggregateType.toLowerCase() + "Id", aggregateId);

        try {
            asyncOutboxPublisher.publishAsync(outbox.getId(), topic, partitionKey, payload);
        } catch (RejectedExecutionException e) {
            log.warn("asyncExecutor 포화로 즉시 발행 건너뜀, relay 스케줄러가 재시도: outboxId={}", outbox.getId());
        }
    }

    private String toPayload(String eventType, Object event) {
        try {
            ObjectNode node = objectMapper.valueToTree(event);
            node.put("eventType", eventType);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("이벤트 직렬화 실패", e);
        }
    }
}

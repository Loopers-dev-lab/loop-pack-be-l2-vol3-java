package com.loopers.interfaces.consumer;

import com.loopers.confg.kafka.EventTypes;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.infrastructure.idempotency.EventHandledJpaEntity;
import com.loopers.infrastructure.idempotency.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.infrastructure.ranking.RankingScoreUpdater;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ProductMetricsJpaRepository metricsRepository;
    private final EventHandledJpaRepository eventHandledRepository;
    private final RankingScoreUpdater rankingScoreUpdater;

    public OrderEventConsumer(ProductMetricsJpaRepository metricsRepository,
                              EventHandledJpaRepository eventHandledRepository,
                              RankingScoreUpdater rankingScoreUpdater) {
        this.metricsRepository = metricsRepository;
        this.eventHandledRepository = eventHandledRepository;
        this.rankingScoreUpdater = rankingScoreUpdater;

    }

    @KafkaListener(
            topics = KafkaTopics.ORDER_EVENTS,
            groupId = "streamer-order",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                transactionTemplate.executeWithoutResult(status -> processRecord(record));
            } catch (RuntimeException e) {
                log.error("order-events 처리 실패 - offset: {}, error: {}",
                        record.offset(), e.getMessage(), e);
            }
        }
        ack.acknowledge();
    }

    @SuppressWarnings("unchecked")
    private void processRecord(ConsumerRecord<Object, Object> record) {
        Map<String, Object> message = (Map<String, Object>) record.value();

        Long eventId = toLong(message.get("eventId"));
        String eventType = (String) message.get("eventType");

        if (eventHandledRepository.existsById(eventId)) {
            return;
        }

        switch (eventType) {
            case EventTypes.ORDER_CREATED -> {
                List<Object> productIds = (List<Object>) message.get("productIds");
                if (productIds != null) {
                    for (Object pid : productIds) {
                        Long productId = toLong(pid);
                        metricsRepository.upsertOrderCount(productId, 1);
                        rankingScoreUpdater.incrementOrderScore(productId);
                    }
                }
            }
            case EventTypes.PAYMENT_COMPLETED -> {
                Long orderId = toLong(message.get("orderId"));
                long amount = toLong(message.get("paymentAmount"));
                log.info("결제 완료 집계 - orderId: {}, amount: {}", orderId, amount);
            }
            default -> log.warn("알 수 없는 이벤트 타입: {}", eventType);
        }

        eventHandledRepository.save(new EventHandledJpaEntity(eventId, eventType));
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        return Long.valueOf(String.valueOf(value));
    }
}

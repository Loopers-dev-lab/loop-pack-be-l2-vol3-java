package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsConsumer {

    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"catalog-events", "order-events"},
        groupId = "metrics-collector",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        for (ConsumerRecord<String, byte[]> record : records) {
            try {
                tx.executeWithoutResult(status -> processRecord(record));
            } catch (Exception e) {
                log.error("MetricsConsumer 처리 실패 — topic={}, offset={}", record.topic(), record.offset(), e);
            }
        }

        ack.acknowledge();
    }

    private void processRecord(ConsumerRecord<String, byte[]> record) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String eventId = extractEventId(record, payload);
            String eventType = extractEventType(record, payload);

            // INSERT-first 멱등 패턴: event_handled에 먼저 삽입 시도
            int inserted = entityManager.createNativeQuery(
                "INSERT IGNORE INTO event_handled (event_id, event_type, created_at) VALUES (:eventId, :eventType, NOW(6))"
            ).setParameter("eventId", eventId)
             .setParameter("eventType", eventType)
             .executeUpdate();

            if (inserted == 0) {
                log.debug("이미 처리된 이벤트 — eventId={}", eventId);
                return;
            }

            switch (eventType) {
                case "LIKE_CREATED" -> upsertLikeCount(payload, 1);
                case "LIKE_REMOVED" -> upsertLikeCount(payload, -1);
                case "PRODUCT_VIEWED" -> upsertViewCount(payload);
                case "ORDER_CREATED" -> upsertSalesMetrics(payload, 1);
                case "ORDER_CANCELLED" -> upsertSalesMetrics(payload, -1);
                default -> log.warn("알 수 없는 이벤트 타입: {}", eventType);
            }
        } catch (Exception e) {
            throw new RuntimeException("이벤트 처리 실패", e);
        }
    }

    private void upsertLikeCount(JsonNode payload, int delta) {
        long productId = payload.get("productId").asLong();
        entityManager.createNativeQuery(
            "INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, sales_amount, updated_at) "
                + "VALUES (:productId, :delta, 0, 0, 0, NOW(6)) "
                + "ON DUPLICATE KEY UPDATE like_count = like_count + :delta, updated_at = NOW(6)"
        ).setParameter("productId", productId)
         .setParameter("delta", delta)
         .executeUpdate();
    }

    private void upsertViewCount(JsonNode payload) {
        long productId = payload.get("productId").asLong();
        entityManager.createNativeQuery(
            "INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, sales_amount, updated_at) "
                + "VALUES (:productId, 0, 1, 0, 0, NOW(6)) "
                + "ON DUPLICATE KEY UPDATE view_count = view_count + 1, updated_at = NOW(6)"
        ).setParameter("productId", productId)
         .executeUpdate();
    }

    private void upsertSalesMetrics(JsonNode payload, int direction) {
        JsonNode items = payload.get("items");
        if (items == null || !items.isArray()) return;

        for (JsonNode item : items) {
            long productId = item.get("productId").asLong();
            int quantity = item.get("quantity").asInt();
            int price = item.get("price").asInt();
            long amount = (long) quantity * price;

            entityManager.createNativeQuery(
                "INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, sales_amount, updated_at) "
                    + "VALUES (:productId, 0, 0, :salesCount, :salesAmount, NOW(6)) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "sales_count = sales_count + :salesCount, "
                    + "sales_amount = sales_amount + :salesAmount, "
                    + "updated_at = NOW(6)"
            ).setParameter("productId", productId)
             .setParameter("salesCount", quantity * direction)
             .setParameter("salesAmount", amount * direction)
             .executeUpdate();
        }
    }

    private String extractEventId(ConsumerRecord<String, byte[]> record, JsonNode payload) {
        // Debezium Outbox: header에 id가 포함됨, 직접 발행: payload에 eventId
        if (payload.has("eventId")) {
            return payload.get("eventId").asText();
        }
        // fallback: topic + partition + offset 조합
        return record.topic() + "-" + record.partition() + "-" + record.offset();
    }

    private String extractEventType(ConsumerRecord<String, byte[]> record, JsonNode payload) {
        if (payload.has("eventType")) {
            return payload.get("eventType").asText();
        }
        // Debezium header에서 eventType 추출 시도
        var headers = record.headers();
        var eventTypeHeader = headers.lastHeader("eventType");
        if (eventTypeHeader != null) {
            return new String(eventTypeHeader.value());
        }
        return "UNKNOWN";
    }
}

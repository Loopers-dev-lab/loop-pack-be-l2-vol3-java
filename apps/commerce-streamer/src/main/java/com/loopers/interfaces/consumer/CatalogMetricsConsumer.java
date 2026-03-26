package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.infrastructure.dlq.DlqPublisher;
import com.loopers.infrastructure.event.EventHandledEntity;
import com.loopers.infrastructure.event.EventHandledJpaRepository;
import com.loopers.infrastructure.product.ProductMetricsEntity;
import com.loopers.infrastructure.product.ProductMetricsJpaRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 카탈로그 메트릭 Consumer — product_metrics upsert
 *
 * 멱등성 보장:
 *   increment 연산(+1)은 멱등하지 않다. 같은 이벤트를 2번 처리하면 +2.
 *   → event_handled 테이블에 Outbox ID를 기록하여 중복 처리 방지.
 *   → increment + event_handled INSERT를 같은 @Transactional로 묶어 원자성 보장.
 *
 * 중복 시나리오:
 *   DB 적재 성공 → ACK 전송 중 네트워크 장애 → Consumer 재시작 → 같은 레코드 재수신
 *   → event_handled에 이미 있으면 스킵 → 중복 increment 방지
 */
@Component
public class CatalogMetricsConsumer {

    private static final Logger log = LoggerFactory.getLogger(CatalogMetricsConsumer.class);

    private final ObjectMapper objectMapper;
    private final ProductMetricsJpaRepository productMetricsRepository;
    private final EventHandledJpaRepository eventHandledRepository;
    private final DlqPublisher dlqPublisher;

    public CatalogMetricsConsumer(ObjectMapper objectMapper,
                                   ProductMetricsJpaRepository productMetricsRepository,
                                   EventHandledJpaRepository eventHandledRepository,
                                   DlqPublisher dlqPublisher) {
        this.objectMapper = objectMapper;
        this.productMetricsRepository = productMetricsRepository;
        this.eventHandledRepository = eventHandledRepository;
        this.dlqPublisher = dlqPublisher;
    }

    @KafkaListener(
            topics = "catalog-events-v1",
            groupId = "catalog-metrics-group",
            containerFactory = "BATCH_LISTENER_DEFAULT"
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                processRecord(record);
            } catch (Exception e) {
                log.error("[CatalogMetrics] 처리 실패 → DLQ — partition={}, offset={}, error={}",
                        record.partition(), record.offset(), e.getMessage(), e);
                dlqPublisher.sendToDlq(record, e);
            }
        }
        ack.acknowledge();
    }

    @Transactional
    protected void processRecord(ConsumerRecord<Object, Object> record) {
        String eventType = getHeader(record, "X-Event-Type");
        String outboxId = getHeader(record, "X-Outbox-Id");
        String payload = record.value().toString();

        if (eventType == null) {
            log.warn("[CatalogMetrics] X-Event-Type 헤더 없음 — partition={}, offset={}",
                    record.partition(), record.offset());
            return;
        }

        // 멱등성 체크 — increment는 멱등하지 않으므로 반드시 중복 방지
        if (outboxId != null && eventHandledRepository.existsByEventId(outboxId)) {
            log.warn("[CatalogMetrics] 중복 스킵 — outboxId={}, partition={}, offset={}",
                    outboxId, record.partition(), record.offset());
            return;
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("[CatalogMetrics] JSON 파싱 실패 — payload={}", payload, e);
            return;
        }

        switch (eventType) {
            case "ProductLikedEvent" -> handleProductLiked(node);
            case "ProductUnlikedEvent" -> handleProductUnliked(node);
            case "OrderItemSoldEvent" -> handleOrderItemSold(node);
            default -> log.warn("[CatalogMetrics] 알 수 없는 eventType={}", eventType);
        }

        // 멱등성 기록 — increment와 같은 TX
        if (outboxId != null) {
            eventHandledRepository.save(EventHandledEntity.of(outboxId, "catalog-events-v1"));
        }
    }

    private void handleProductLiked(JsonNode node) {
        Long productId = node.path("productId").asLong();
        ProductMetricsEntity metrics = getOrCreateMetrics(productId);
        metrics.incrementLikeCount();
        productMetricsRepository.save(metrics);
        log.info("[CatalogMetrics] 좋아요 집계 완료 — productId={}, likeCount={}",
                productId, metrics.getLikeCount());
    }

    private void handleProductUnliked(JsonNode node) {
        Long productId = node.path("productId").asLong();
        ProductMetricsEntity metrics = getOrCreateMetrics(productId);
        metrics.decrementLikeCount();
        productMetricsRepository.save(metrics);
        log.info("[CatalogMetrics] 좋아요 취소 집계 완료 — productId={}, likeCount={}",
                productId, metrics.getLikeCount());
    }

    private void handleOrderItemSold(JsonNode node) {
        Long productId = node.path("productId").asLong();
        int quantity = node.path("quantity").asInt(1);
        ProductMetricsEntity metrics = getOrCreateMetrics(productId);
        metrics.addSalesCount(quantity);
        productMetricsRepository.save(metrics);
        log.info("[CatalogMetrics] 판매량 집계 완료 — productId={}, salesCount={}",
                productId, metrics.getSalesCount());
    }

    private ProductMetricsEntity getOrCreateMetrics(Long productId) {
        return productMetricsRepository.findById(productId)
                .orElseGet(() -> productMetricsRepository.save(ProductMetricsEntity.create(productId)));
    }

    private String getHeader(ConsumerRecord<Object, Object> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}

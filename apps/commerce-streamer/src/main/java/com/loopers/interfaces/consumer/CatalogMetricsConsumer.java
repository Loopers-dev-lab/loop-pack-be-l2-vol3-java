package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * catalog-events-v1 토픽에서 좋아요/판매량 이벤트를 소비하여
 * product_metrics 테이블에 집계한다.
 *
 * Relay가 Kafka 헤더에 X-Event-Type을 포함하므로
 * payload 파싱 없이 이벤트 타입을 판별한다.
 *
 * key=productId → 같은 상품의 이벤트는 같은 파티션 → 순차 처리
 * → product_metrics 동시 UPDATE 방지
 */
@Component
public class CatalogMetricsConsumer {

    private static final Logger log = LoggerFactory.getLogger(CatalogMetricsConsumer.class);

    private final ObjectMapper objectMapper;
    private final ProductMetricsJpaRepository productMetricsRepository;

    public CatalogMetricsConsumer(ObjectMapper objectMapper,
                                   ProductMetricsJpaRepository productMetricsRepository) {
        this.objectMapper = objectMapper;
        this.productMetricsRepository = productMetricsRepository;
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
                log.error("[CatalogMetrics] 처리 실패 — partition={}, offset={}, error={}",
                        record.partition(), record.offset(), e.getMessage(), e);
            }
        }
        ack.acknowledge();
    }

    @Transactional
    protected void processRecord(ConsumerRecord<Object, Object> record) {
        String eventType = getHeader(record, "X-Event-Type");
        String payload = record.value().toString();

        if (eventType == null) {
            log.warn("[CatalogMetrics] X-Event-Type 헤더 없음 — partition={}, offset={}",
                    record.partition(), record.offset());
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

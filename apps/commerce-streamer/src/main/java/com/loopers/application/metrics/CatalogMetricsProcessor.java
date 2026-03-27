package com.loopers.application.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.infrastructure.event.EventHandledEntity;
import com.loopers.infrastructure.event.EventHandledJpaRepository;
import com.loopers.infrastructure.product.ProductMetricsEntity;
import com.loopers.infrastructure.product.ProductMetricsJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카탈로그 메트릭 처리 — @Transactional 보장
 *
 * Consumer(Interfaces)에서 분리된 비즈니스 처리 Bean.
 * Consumer → Processor 위임으로 self-invocation 방지.
 *
 * Consumer가 this.processRecord()를 호출하면 @Transactional이 무시되지만,
 * 별도 Bean인 Processor를 프록시를 통해 호출하면 @Transactional이 정상 동작.
 *
 * increment + event_handled INSERT가 같은 TX:
 *   → 하나 실패 → 전체 롤백 → 재처리 시 정합성 유지
 *   → increment만 커밋되고 event_handled가 실패하는 시나리오 방지
 */
@Service
public class CatalogMetricsProcessor {

    private static final Logger log = LoggerFactory.getLogger(CatalogMetricsProcessor.class);

    private final ObjectMapper objectMapper;
    private final ProductMetricsJpaRepository productMetricsRepository;
    private final EventHandledJpaRepository eventHandledRepository;

    public CatalogMetricsProcessor(ObjectMapper objectMapper,
                                    ProductMetricsJpaRepository productMetricsRepository,
                                    EventHandledJpaRepository eventHandledRepository) {
        this.objectMapper = objectMapper;
        this.productMetricsRepository = productMetricsRepository;
        this.eventHandledRepository = eventHandledRepository;
    }

    /**
     * 메트릭 이벤트 처리 — 같은 TX에서 increment + 멱등성 기록
     *
     * @return true = 처리됨, false = 중복 스킵
     */
    @Transactional
    public boolean process(String eventType, String outboxId, String payload) {
        // 멱등성 체크 — increment는 멱등하지 않으므로 반드시 중복 방지
        if (outboxId != null && eventHandledRepository.existsByEventId(outboxId)) {
            log.warn("[MetricsProcessor] 중복 스킵 — outboxId={}", outboxId);
            return false;
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("[MetricsProcessor] JSON 파싱 실패 — payload={}", payload, e);
            return false;
        }

        switch (eventType) {
            case "ProductLikedEvent" -> handleProductLiked(node);
            case "ProductUnlikedEvent" -> handleProductUnliked(node);
            case "OrderItemSoldEvent" -> handleOrderItemSold(node);
            default -> {
                log.warn("[MetricsProcessor] 알 수 없는 eventType={}", eventType);
                return false;
            }
        }

        // 멱등성 기록 — increment와 같은 TX (핵심!)
        if (outboxId != null) {
            eventHandledRepository.save(EventHandledEntity.of(outboxId, "catalog-events-v1"));
        }

        return true;
    }

    private void handleProductLiked(JsonNode node) {
        Long productId = node.path("productId").asLong();
        ProductMetricsEntity metrics = getOrCreateMetrics(productId);
        metrics.incrementLikeCount();
        productMetricsRepository.save(metrics);
        log.info("[MetricsProcessor] 좋아요 집계 완료 — productId={}, likeCount={}",
                productId, metrics.getLikeCount());
    }

    private void handleProductUnliked(JsonNode node) {
        Long productId = node.path("productId").asLong();
        ProductMetricsEntity metrics = getOrCreateMetrics(productId);
        metrics.decrementLikeCount();
        productMetricsRepository.save(metrics);
        log.info("[MetricsProcessor] 좋아요 취소 집계 완료 — productId={}, likeCount={}",
                productId, metrics.getLikeCount());
    }

    private void handleOrderItemSold(JsonNode node) {
        Long productId = node.path("productId").asLong();
        int quantity = node.path("quantity").asInt(1);
        ProductMetricsEntity metrics = getOrCreateMetrics(productId);
        metrics.addSalesCount(quantity);
        productMetricsRepository.save(metrics);
        log.info("[MetricsProcessor] 판매량 집계 완료 — productId={}, salesCount={}",
                productId, metrics.getSalesCount());
    }

    private ProductMetricsEntity getOrCreateMetrics(Long productId) {
        return productMetricsRepository.findById(productId)
                .orElseGet(() -> productMetricsRepository.save(ProductMetricsEntity.create(productId)));
    }
}

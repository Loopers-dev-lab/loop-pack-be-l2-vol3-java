package com.loopers.interfaces.consumer;

import com.loopers.domain.metrics.ProductMetricsService;
import com.loopers.infrastructure.monitoring.ConsumerMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 카탈로그 이벤트 프로세서.
 *
 * <p>이벤트 타입에 따라 상품 지표(product_metrics)를 갱신한다.
 * 비핵심 지표이므로 Outbox/멱등 처리 없이 at-least-once로 처리한다.
 * (중복 수신 시 카운트가 약간 부풀려질 수 있으나, 주기적 보정 배치가 보완한다.)</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogEventProcessor {

    private final ProductMetricsService productMetricsService;
    private final ConsumerMetrics consumerMetrics;

    @Transactional
    @SuppressWarnings("unchecked")
    public void process(ConsumerRecord<Object, Object> record) {
        Object value = record.value();
        if (!(value instanceof Map)) {
            log.warn("[CatalogProcessor] 예상치 못한 메시지 타입: {}", value != null ? value.getClass() : "null");
            return;
        }

        Map<String, Object> message = (Map<String, Object>) value;
        String eventType = (String) message.get("eventType");
        Number productIdNum = (Number) message.get("productId");

        if (eventType == null || productIdNum == null) {
            log.warn("[CatalogProcessor] eventType 또는 productId 누락: {}", message);
            return;
        }

        Long productId = productIdNum.longValue();

        switch (eventType) {
            case "PRODUCT_VIEWED" -> {
                productMetricsService.incrementViewCount(productId);
                log.debug("[CatalogProcessor] PRODUCT_VIEWED productId={}", productId);
            }
            case "PRODUCT_LIKED" -> {
                productMetricsService.incrementLikeCount(productId);
                log.debug("[CatalogProcessor] PRODUCT_LIKED productId={}", productId);
            }
            case "PRODUCT_UNLIKED" -> {
                productMetricsService.decrementLikeCount(productId);
                log.debug("[CatalogProcessor] PRODUCT_UNLIKED productId={}", productId);
            }
            default -> log.warn("[CatalogProcessor] 알 수 없는 eventType: {}", eventType);
        }
    }
}

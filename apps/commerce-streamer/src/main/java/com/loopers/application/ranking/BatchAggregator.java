package com.loopers.application.ranking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka 배치 poll() 결과를 상품별 `MetricDelta` 로 압축하는 유틸.
 *
 * <p>지원 이벤트:
 * <ul>
 *   <li>catalog-events: {@code PRODUCT_VIEWED}, {@code PRODUCT_LIKED}</li>
 *   <li>order-events:   {@code ORDER_PAID}</li>
 * </ul>
 *
 * <p>각 payload 는 {@code { eventId, eventType, data }} 구조를 가지며,
 * `data` 안의 필드는 이벤트 유형에 따라 다르다. 파싱 실패/필수 필드 누락은
 * warn 로그 후 skip 하여 배치 전체가 실패하지 않도록 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchAggregator {

    private final ObjectMapper objectMapper;

    /**
     * catalog-events 배치를 상품별 델타로 압축한다.
     */
    public Map<Long, MetricDelta> aggregateCatalog(List<ConsumerRecord<String, String>> records) {
        Map<Long, MetricDelta> perProduct = new LinkedHashMap<>();
        if (records == null || records.isEmpty()) return perProduct;

        for (ConsumerRecord<String, String> record : records) {
            JsonNode payload = safeRead(record);
            if (payload == null) continue;

            String eventId = payload.path("eventId").asText(null);
            String eventType = payload.path("eventType").asText(null);
            JsonNode data = payload.path("data");
            if (eventId == null || eventType == null || data.isMissingNode()) {
                log.warn("catalog-events: eventId/eventType/data 누락 — skip. value={}", record.value());
                continue;
            }

            switch (eventType) {
                case "PRODUCT_VIEWED" -> {
                    Long productId = longOrNull(data, "productId");
                    if (productId == null) continue;
                    MetricDelta delta = perProduct.computeIfAbsent(productId, k -> new MetricDelta());
                    delta.addView(1);
                    delta.rememberEventId(eventId);
                }
                case "PRODUCT_LIKED" -> {
                    Long productId = longOrNull(data, "productId");
                    if (productId == null) continue;
                    boolean liked = data.path("liked").asBoolean(true);
                    MetricDelta delta = perProduct.computeIfAbsent(productId, k -> new MetricDelta());
                    delta.addLike(liked ? 1 : -1);
                    delta.rememberEventId(eventId);
                }
                default -> log.warn("catalog-events: 알 수 없는 eventType={} — skip", eventType);
            }
        }
        return perProduct;
    }

    /**
     * order-events 배치를 상품별 델타로 압축한다.
     *
     * <p>ORDER_PAID payload 예시:
     * <pre>
     * {
     *   "eventId": "uuid",
     *   "eventType": "ORDER_PAID",
     *   "data": {
     *     "orderedProducts": [
     *       { "productId": 1, "quantity": 2, "unitPrice": 10000 },
     *       ...
     *     ]
     *   }
     * }
     * </pre>
     *
     * <p>`unitPrice` 가 누락된 메시지(과거 호환) 는 0 으로 fallback 한다 — 금액 가중치는
     * 해당 건만 0 이 되며 건수(orderCount) 는 정상 반영된다. 정상 publisher 는 R9 부터 항상 채워 보낸다.
     */
    public Map<Long, MetricDelta> aggregateOrder(List<ConsumerRecord<String, String>> records) {
        Map<Long, MetricDelta> perProduct = new LinkedHashMap<>();
        if (records == null || records.isEmpty()) return perProduct;

        for (ConsumerRecord<String, String> record : records) {
            JsonNode payload = safeRead(record);
            if (payload == null) continue;

            String eventId = payload.path("eventId").asText(null);
            String eventType = payload.path("eventType").asText(null);
            JsonNode data = payload.path("data");
            if (eventId == null || eventType == null || data.isMissingNode()) {
                log.warn("order-events: eventId/eventType/data 누락 — skip. value={}", record.value());
                continue;
            }
            if (!"ORDER_PAID".equals(eventType)) {
                log.warn("order-events: 알 수 없는 eventType={} — skip", eventType);
                continue;
            }

            JsonNode items = data.path("orderedProducts");
            if (!items.isArray() || items.isEmpty()) {
                log.warn("order-events: orderedProducts 비정상 — skip. eventId={}", eventId);
                continue;
            }

            for (JsonNode item : items) {
                Long productId = longOrNull(item, "productId");
                if (productId == null) continue;
                long quantity = item.path("quantity").asLong(0L);
                if (quantity <= 0) continue;

                BigDecimal unitPrice = decimalOrZero(item, "unitPrice");
                BigDecimal lineAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));

                MetricDelta delta = perProduct.computeIfAbsent(productId, k -> new MetricDelta());
                delta.addOrder(quantity, lineAmount);
                delta.rememberEventId(eventId);
            }
        }
        return perProduct;
    }

    /**
     * 단일 레코드에서 eventId 만 빠르게 추출한다 — 멱등 필터 용도.
     * 파싱 실패 시 null.
     */
    public String extractEventId(ConsumerRecord<String, String> record) {
        JsonNode payload = safeRead(record);
        if (payload == null) return null;
        String eventId = payload.path("eventId").asText(null);
        return (eventId == null || eventId.isBlank()) ? null : eventId;
    }

    /**
     * 배치에서 모든 대상 productId 를 추출한다 (성공/재시도 여부 무관).
     */
    public java.util.Set<Long> extractProductIds(List<ConsumerRecord<String, String>> records) {
        java.util.Set<Long> productIds = new java.util.HashSet<>();
        if (records == null || records.isEmpty()) return productIds;

        for (ConsumerRecord<String, String> record : records) {
            JsonNode payload = safeRead(record);
            if (payload == null) continue;
            
            JsonNode data = payload.path("data");
            if (data.isMissingNode() || data.isNull()) continue;

            String eventType = payload.path("eventType").asText("");
            if ("ORDER_PAID".equals(eventType)) {
                JsonNode items = data.path("orderedProducts");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        Long pid = longOrNull(item, "productId");
                        if (pid != null) productIds.add(pid);
                    }
                }
            } else {
                Long pid = longOrNull(data, "productId");
                if (pid != null) productIds.add(pid);
            }
        }
        return productIds;
    }

    private JsonNode safeRead(ConsumerRecord<String, String> record) {
        try {
            return objectMapper.readTree(record.value());
        } catch (JsonProcessingException e) {
            log.error("payload JSON 파싱 실패 — skip. value={}", record.value(), e);
            return null;
        }
    }

    private Long longOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (value.isIntegralNumber()) return value.longValue();
        if (value.isTextual()) {
            try {
                return Long.parseLong(value.textValue());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal decimalOrZero(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return BigDecimal.ZERO;
        if (value.isNumber()) return value.decimalValue();
        try {
            return new BigDecimal(value.asText("0"));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}

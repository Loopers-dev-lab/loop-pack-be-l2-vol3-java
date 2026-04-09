package com.loopers.interfaces.consumer;

import com.loopers.confg.kafka.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 카탈로그/주문 이벤트를 소비하여 product_metrics에 집계하는 Consumer.
 *
 * <p>Phase 1: 건별 멱등성 체크(event_handled INSERT IGNORE) + productId별 메모리 집계.
 * Phase 2: productId별 1회 UPSERT로 DB 쓰기 횟수를 감소시킨다.</p>
 *
 * <p>3,000건 poll, 인기 상품 100개에 이벤트 집중 시:
 * [기존] 건별 UPSERT: event_handled 3,000회 + product_metrics 3,000회 = ~6,000회
 * [개선] 집계 UPSERT: event_handled 3,000회 + product_metrics ~100회 = ~3,100회 (48% 감소)</p>
 */
@Slf4j
@Component
public class MetricsConsumer {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public MetricsConsumer(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @KafkaListener(
        topics = {"catalog-events", "order-events"},
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        Map<Long, MetricsDelta> deltaMap = new HashMap<>();

        // Phase 1: 멱등성 체크 + 메모리 집계
        for (ConsumerRecord<String, String> record : records) {
            try {
                processRecord(record, deltaMap);
            } catch (Exception e) {
                log.error("이벤트 처리 실패: topic={}, offset={}, value={}",
                    record.topic(), record.offset(), record.value(), e);
            }
        }

        // Phase 2: productId별 1회 UPSERT
        if (!deltaMap.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> {
                for (Map.Entry<Long, MetricsDelta> entry : deltaMap.entrySet()) {
                    Long productId = entry.getKey();
                    MetricsDelta delta = entry.getValue();
                    upsertProductMetrics(productId, delta);
                }
            });
        }

        ack.acknowledge();
        log.debug("메트릭스 배치 처리 완료: records={}, products={}", records.size(), deltaMap.size());
    }

    private void processRecord(ConsumerRecord<String, String> record, Map<Long, MetricsDelta> deltaMap) {
        String eventId = extractField(record.value(), "eventId");
        String eventType = extractField(record.value(), "eventType");
        String productIdStr = extractField(record.value(), "productId");

        if (eventId == null || eventType == null || productIdStr == null) {
            log.warn("필수 필드 누락: value={}", record.value());
            return;
        }

        Long productId = Long.parseLong(productIdStr);

        transactionTemplate.executeWithoutResult(status -> {
            // 멱등성 체크: INSERT IGNORE
            int inserted = jdbcTemplate.update(
                "INSERT IGNORE INTO event_handled (event_id, event_type, handled_at) VALUES (?, ?, NOW())",
                eventId, eventType
            );

            if (inserted > 0) {
                // 새 이벤트만 집계
                switch (eventType) {
                    case "LIKE_CREATED" -> deltaMap.merge(productId,
                        MetricsDelta.ofLike(1), MetricsDelta::merge);
                    case "LIKE_REMOVED" -> deltaMap.merge(productId,
                        MetricsDelta.ofLike(-1), MetricsDelta::merge);
                    case "PRODUCT_VIEWED" -> deltaMap.merge(productId,
                        MetricsDelta.ofView(), MetricsDelta::merge);
                    case "ORDER_CREATED" -> {
                        int salesCount = parseIntField(record.value(), "salesCount", 1);
                        long salesAmount = parseLongField(record.value(), "salesAmount", 0);
                        deltaMap.merge(productId,
                            MetricsDelta.ofSales(salesCount, salesAmount), MetricsDelta::merge);
                    }
                    case "ORDER_CANCELLED" -> {
                        int salesCount = parseIntField(record.value(), "salesCount", 1);
                        long salesAmount = parseLongField(record.value(), "salesAmount", 0);
                        deltaMap.merge(productId,
                            MetricsDelta.ofSales(-salesCount, -salesAmount), MetricsDelta::merge);
                    }
                    default -> log.warn("알 수 없는 이벤트 타입: {}", eventType);
                }
            }
        });
    }

    private void upsertProductMetrics(Long productId, MetricsDelta delta) {
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, sales_amount) " +
            "VALUES (?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE " +
            "like_count = like_count + VALUES(like_count), " +
            "view_count = view_count + VALUES(view_count), " +
            "sales_count = sales_count + VALUES(sales_count), " +
            "sales_amount = sales_amount + VALUES(sales_amount)",
            productId, delta.likeDelta, delta.viewDelta, delta.salesCountDelta, delta.salesAmountDelta
        );
    }

    private String extractField(String json, String fieldName) {
        // 간단한 JSON 필드 추출 (ObjectMapper 없이 경량 처리)
        String pattern = "\"" + fieldName + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx == -1) return null;

        int start = colonIdx + 1;
        // skip whitespace
        while (start < json.length() && json.charAt(start) == ' ') start++;

        if (start >= json.length()) return null;

        if (json.charAt(start) == '"') {
            // string value
            int end = json.indexOf('"', start + 1);
            return end == -1 ? null : json.substring(start + 1, end);
        } else {
            // numeric or other
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(start, end).trim();
        }
    }

    private int parseIntField(String json, String fieldName, int defaultValue) {
        String value = extractField(json, fieldName);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long parseLongField(String json, String fieldName, long defaultValue) {
        String value = extractField(json, fieldName);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static class MetricsDelta {
        int likeDelta = 0;
        int viewDelta = 0;
        int salesCountDelta = 0;
        long salesAmountDelta = 0;

        static MetricsDelta ofLike(int delta) {
            MetricsDelta d = new MetricsDelta();
            d.likeDelta = delta;
            return d;
        }

        static MetricsDelta ofView() {
            MetricsDelta d = new MetricsDelta();
            d.viewDelta = 1;
            return d;
        }

        static MetricsDelta ofSales(int count, long amount) {
            MetricsDelta d = new MetricsDelta();
            d.salesCountDelta = count;
            d.salesAmountDelta = amount;
            return d;
        }

        static MetricsDelta merge(MetricsDelta a, MetricsDelta b) {
            MetricsDelta result = new MetricsDelta();
            result.likeDelta = a.likeDelta + b.likeDelta;
            result.viewDelta = a.viewDelta + b.viewDelta;
            result.salesCountDelta = a.salesCountDelta + b.salesCountDelta;
            result.salesAmountDelta = a.salesAmountDelta + b.salesAmountDelta;
            return result;
        }
    }
}

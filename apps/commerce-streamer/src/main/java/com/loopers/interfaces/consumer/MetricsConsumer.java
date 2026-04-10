package com.loopers.interfaces.consumer;

import com.loopers.application.ranking.MetricsDelta;
import com.loopers.application.ranking.RankingScoreUpdater;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
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
 *
 * <p>Late-Arriving Fact: ORDER_CANCELLED 이벤트는 인식일(CURDATE) + 발생일(원주문일) 이중 UPSERT.</p>
 */
@Slf4j
@Component
public class MetricsConsumer {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final RankingScoreUpdater rankingScoreUpdater;

    public MetricsConsumer(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate,
                           RankingScoreUpdater rankingScoreUpdater) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.rankingScoreUpdater = rankingScoreUpdater;
    }

    @KafkaListener(
        topics = {"catalog-events", "order-events"},
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        Map<Long, MetricsDelta> deltaMap = new HashMap<>();
        List<LateArrivingCancel> lateArrivingCancels = new ArrayList<>();

        // Phase 1: 멱등성 체크 + 메모리 집계
        for (ConsumerRecord<String, String> record : records) {
            try {
                processRecord(record, deltaMap, lateArrivingCancels);
            } catch (Exception e) {
                log.error("이벤트 처리 실패: topic={}, offset={}, value={}",
                    record.topic(), record.offset(), record.value(), e);
            }
        }

        // Phase 2: productId별 인식일 UPSERT + 발생일(원주문일) UPSERT
        if (!deltaMap.isEmpty() || !lateArrivingCancels.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> {
                for (Map.Entry<Long, MetricsDelta> entry : deltaMap.entrySet()) {
                    upsertProductMetrics(entry.getKey(), entry.getValue());
                }
                for (LateArrivingCancel cancel : lateArrivingCancels) {
                    upsertCancelByOrderDate(cancel);
                }
            });
        }

        // Phase 3: Redis 랭킹 ZSET 갱신 (Redis 장애가 DB 커밋에 영향 주지 않도록 격리)
        if (!deltaMap.isEmpty()) {
            try {
                rankingScoreUpdater.update(deltaMap);
            } catch (Exception e) {
                log.warn("랭킹 스코어 갱신 실패 (DB 메트릭스는 정상 반영됨): products={}", deltaMap.size(), e);
            }
        }

        ack.acknowledge();
        log.debug("메트릭스 배치 처리 완료: records={}, products={}, lateArrivals={}",
            records.size(), deltaMap.size(), lateArrivingCancels.size());
    }

    private void processRecord(ConsumerRecord<String, String> record,
                               Map<Long, MetricsDelta> deltaMap,
                               List<LateArrivingCancel> lateArrivingCancels) {
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
                        MetricsDelta.ofLike(), MetricsDelta::merge);
                    case "LIKE_REMOVED" -> deltaMap.merge(productId,
                        MetricsDelta.ofUnlike(), MetricsDelta::merge);
                    case "PRODUCT_VIEWED" -> deltaMap.merge(productId,
                        MetricsDelta.ofView(), MetricsDelta::merge);
                    case "ORDER_CREATED" -> {
                        int salesCount = parseIntField(record.value(), "salesCount", 1);
                        long salesAmount = parseLongField(record.value(), "salesAmount", 0);
                        deltaMap.merge(productId,
                            MetricsDelta.ofSales(salesCount, salesAmount), MetricsDelta::merge);
                    }
                    case "ORDER_CANCELLED" -> {
                        int cancelCount = parseIntField(record.value(), "salesCount", 1);
                        long cancelAmount = parseLongField(record.value(), "salesAmount", 0);
                        deltaMap.merge(productId,
                            MetricsDelta.ofCancel(cancelCount, cancelAmount), MetricsDelta::merge);

                        // Late-Arriving Fact: 발생일(원주문일) 기준 별도 수집
                        String originalOrderDateStr = extractField(record.value(), "originalOrderDate");
                        if (originalOrderDateStr != null) {
                            try {
                                LocalDate orderDate = LocalDate.parse(originalOrderDateStr);
                                lateArrivingCancels.add(
                                    new LateArrivingCancel(productId, orderDate, cancelCount, cancelAmount));
                            } catch (Exception e) {
                                log.warn("originalOrderDate 파싱 실패: productId={}, value={}",
                                    productId, originalOrderDateStr, e);
                            }
                        }
                    }
                    default -> log.warn("알 수 없는 이벤트 타입: {}", eventType);
                }
            }
        });
    }

    private void upsertProductMetrics(Long productId, MetricsDelta delta) {
        jdbcTemplate.update(
            "INSERT INTO product_metrics " +
            "(product_id, metric_date, view_count, like_count, unlike_count, " +
            " sales_count, sales_amount, cancel_count_by_event_date, cancel_amount_by_event_date) " +
            "VALUES (?, CURDATE(), ?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE " +
            "view_count                  = view_count                  + VALUES(view_count), " +
            "like_count                  = like_count                  + VALUES(like_count), " +
            "unlike_count               = unlike_count               + VALUES(unlike_count), " +
            "sales_count                = sales_count                + VALUES(sales_count), " +
            "sales_amount               = sales_amount               + VALUES(sales_amount), " +
            "cancel_count_by_event_date = cancel_count_by_event_date + VALUES(cancel_count_by_event_date), " +
            "cancel_amount_by_event_date = cancel_amount_by_event_date + VALUES(cancel_amount_by_event_date)",
            productId,
            delta.getViewDelta(), delta.getLikeDelta(), delta.getUnlikeDelta(),
            delta.getSalesCountDelta(), delta.getSalesAmountDelta(),
            delta.getCancelCountDelta(), delta.getCancelAmountDelta()
        );
    }

    private void upsertCancelByOrderDate(LateArrivingCancel cancel) {
        jdbcTemplate.update(
            "INSERT INTO product_metrics " +
            "(product_id, metric_date, cancel_count_by_order_date, cancel_amount_by_order_date) " +
            "VALUES (?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE " +
            "cancel_count_by_order_date  = cancel_count_by_order_date  + VALUES(cancel_count_by_order_date), " +
            "cancel_amount_by_order_date = cancel_amount_by_order_date + VALUES(cancel_amount_by_order_date)",
            cancel.productId, cancel.orderDate, cancel.count, cancel.amount
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

    private record LateArrivingCancel(Long productId, LocalDate orderDate, int count, long amount) {}
}

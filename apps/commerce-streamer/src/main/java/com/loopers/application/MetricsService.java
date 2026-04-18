package com.loopers.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.eventtracker.AggregateEventTrackerRepository;
import com.loopers.domain.metrics.DailyProductMetricsRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final ProductMetricsRepository productMetricsRepository;
    private final DailyProductMetricsRepository dailyProductMetricsRepository;
    private final EventHandledRepository eventHandledRepository;
    private final AggregateEventTrackerRepository aggregateEventTrackerRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processEvent(String eventId, String eventType, String aggregateId, ZonedDateTime eventCreatedAt) {
        processEvent(eventId, eventType, aggregateId, eventCreatedAt, null);
    }

    @Transactional
    public void processEvent(String eventId, String eventType, String aggregateId,
                             ZonedDateTime eventCreatedAt, String payload) {
        try {
            eventHandledRepository.save(eventId);
        } catch (DataIntegrityViolationException e) {
            log.debug("이미 처리된 이벤트: eventId={}", eventId);
            return;
        }

        Long targetId = Long.parseLong(aggregateId);
        LocalDate metricDate = eventCreatedAt.toLocalDate();

        switch (eventType) {
            case "PRODUCT_VIEWED" -> {
                productMetricsRepository.incrementViewCount(targetId, 1);
                recordDailyMetrics(() -> dailyProductMetricsRepository.incrementViewCount(targetId, 1, metricDate));
                aggregateEventTrackerRepository.upsert(aggregateId, eventType, eventCreatedAt);
            }
            case "PRODUCT_LIKED" -> {
                productMetricsRepository.incrementLikeCount(targetId, 1);
                recordDailyMetrics(() -> dailyProductMetricsRepository.incrementLikeCount(targetId, 1, metricDate));
                aggregateEventTrackerRepository.upsert(aggregateId, eventType, eventCreatedAt);
            }
            case "PRODUCT_UNLIKED" -> {
                productMetricsRepository.incrementLikeCount(targetId, -1);
                recordDailyMetrics(() -> dailyProductMetricsRepository.incrementLikeCount(targetId, -1, metricDate));
                aggregateEventTrackerRepository.upsert(aggregateId, eventType, eventCreatedAt);
            }
            case "ORDER_COMPLETED" -> processOrderCompleted(aggregateId, eventType, eventCreatedAt, payload);
            default -> log.warn("알 수 없는 이벤트 타입: eventType={}", eventType);
        }
    }

    private void processOrderCompleted(String aggregateId, String eventType,
                                       ZonedDateTime eventCreatedAt, String payload) {
        LocalDate metricDate = eventCreatedAt.toLocalDate();
        if (!aggregateEventTrackerRepository.isNewerEvent(aggregateId, eventType, eventCreatedAt)) {
            log.debug("이미 최신 이벤트가 처리됨: aggregateId={}, eventType={}", aggregateId, eventType);
            aggregateEventTrackerRepository.upsert(aggregateId, eventType, eventCreatedAt);
            return;
        }

        aggregateEventTrackerRepository.upsert(aggregateId, eventType, eventCreatedAt);

        if (payload == null) {
            log.warn("ORDER_COMPLETED 이벤트에 payload가 없습니다: aggregateId={}", aggregateId);
            return;
        }

        try {
            JsonNode payloadNode = objectMapper.readTree(payload);
            JsonNode productIdsNode = payloadNode.get("productIds");
            long totalAmount = payloadNode.get("totalAmount").asLong();

            if (productIdsNode != null && productIdsNode.isArray() && !productIdsNode.isEmpty()) {
                long amountPerProduct = totalAmount / productIdsNode.size();
                for (JsonNode productIdNode : productIdsNode) {
                    long productId = productIdNode.asLong();
                    productMetricsRepository.incrementSalesCount(productId, 1, amountPerProduct);
                    recordDailyMetrics(() -> dailyProductMetricsRepository.incrementSalesCount(productId, 1, amountPerProduct, metricDate));
                }
            }

            log.info("주문 완료 이벤트 처리 완료: orderId={}, productCount={}, totalAmount={}",
                aggregateId, productIdsNode != null ? productIdsNode.size() : 0, totalAmount);
        } catch (Exception e) {
            log.error("ORDER_COMPLETED payload 파싱 실패: aggregateId={}", aggregateId, e);
        }
    }

    private void recordDailyMetrics(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("일별 메트릭 기록 실패 (누적 메트릭에는 영향 없음)", e);
        }
    }
}

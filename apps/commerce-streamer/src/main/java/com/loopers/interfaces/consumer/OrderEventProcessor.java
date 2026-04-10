package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.idempotency.EventHandledModel;
import com.loopers.domain.idempotency.EventHandledRepository;
import com.loopers.domain.idempotency.EventLogModel;
import com.loopers.domain.idempotency.EventLogRepository;
import com.loopers.domain.metrics.ProductMetricsService;
import com.loopers.infrastructure.monitoring.ConsumerMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 주문 이벤트 프로세서.
 *
 * <p>Outbox 패턴으로 발행된 주문 이벤트를 멱등하게 처리한다.
 * event_handled 테이블로 중복 처리를 방지하고, event_log에 처리 이력을 기록한다.</p>
 *
 * <p>ORDER_CREATED 이벤트 수신 시 주문 항목별로 product_metrics의
 * order_count/order_amount를 갱신한다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProcessor {

    private final EventHandledRepository eventHandledRepository;
    private final EventLogRepository eventLogRepository;
    private final ProductMetricsService productMetricsService;
    private final ConsumerMetrics consumerMetrics;
    private final ObjectMapper objectMapper;

    @Transactional
    @SuppressWarnings("unchecked")
    public void process(ConsumerRecord<Object, Object> record) {
        Object value = record.value();

        Map<String, Object> envelope;
        if (value instanceof String str) {
            try {
                envelope = objectMapper.readValue(str, Map.class);
            } catch (Exception e) {
                log.warn("[OrderProcessor] JSON 파싱 실패: {}", str, e);
                return;
            }
        } else if (value instanceof Map) {
            envelope = (Map<String, Object>) value;
        } else {
            log.warn("[OrderProcessor] 예상치 못한 메시지 타입: {}", value != null ? value.getClass() : "null");
            return;
        }
        Number eventIdNum = (Number) envelope.get("eventId");
        String eventType = (String) envelope.get("eventType");
        String topic = record.topic();

        if (eventIdNum == null || eventType == null) {
            log.warn("[OrderProcessor] eventId 또는 eventType 누락: {}", envelope);
            return;
        }

        Long eventId = eventIdNum.longValue();

        // 멱등 체크
        if (eventHandledRepository.existsById(eventId)) {
            log.info("[OrderProcessor] 이미 처리된 이벤트 skip — eventId={}", eventId);
            eventLogRepository.save(EventLogModel.skipped(eventId, eventType, topic));
            return;
        }

        try {
            Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");

            switch (eventType) {
                case "ORDER_CREATED" -> handleOrderCreated(payload);
                case "ORDER_CANCELLED" -> handleOrderCancelled(payload);
                case "ORDER_EXPIRED" -> handleOrderExpired(payload);
                default -> log.warn("[OrderProcessor] 알 수 없는 eventType: {}", eventType);
            }

            // 멱등 키 저장 + 성공 로그
            eventHandledRepository.save(new EventHandledModel(eventId));
            eventLogRepository.save(EventLogModel.success(eventId, eventType, topic));
            log.info("[OrderProcessor] 처리 완료 — eventId={}, eventType={}", eventId, eventType);
        } catch (Exception e) {
            eventLogRepository.save(EventLogModel.failed(eventId, eventType, topic, e.getMessage()));
            log.error("[OrderProcessor] 처리 실패 — eventId={}, eventType={}", eventId, eventType, e);
            throw e; // 트랜잭션 롤백
        }
    }

    @SuppressWarnings("unchecked")
    private void handleOrderCreated(Map<String, Object> payload) {
        if (payload == null) return;

        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        if (items == null || items.isEmpty()) return;

        for (Map<String, Object> item : items) {
            Number productIdNum = (Number) item.get("productId");
            Number finalAmountNum = (Number) item.get("finalAmount");

            if (productIdNum != null && finalAmountNum != null) {
                long productId = productIdNum.longValue();
                long amount = new BigDecimal(finalAmountNum.toString()).longValue();
                productMetricsService.incrementOrderCount(productId, amount);
            }
        }
    }

    private void handleOrderCancelled(Map<String, Object> payload) {
        // 주문 취소 시 별도 지표 갱신 없음 (order_count는 감소하지 않음)
        // 필요 시 별도 cancelled_count 컬럼 추가 가능
        log.debug("[OrderProcessor] ORDER_CANCELLED 처리 — payload={}", payload);
    }

    private void handleOrderExpired(Map<String, Object> payload) {
        // 주문 만료 시 별도 지표 갱신 없음
        log.debug("[OrderProcessor] ORDER_EXPIRED 처리 — payload={}", payload);
    }
}

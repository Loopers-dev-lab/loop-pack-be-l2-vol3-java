package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.idempotency.EventHandledModel;
import com.loopers.domain.idempotency.EventHandledRepository;
import com.loopers.domain.idempotency.EventLogModel;
import com.loopers.domain.idempotency.EventLogRepository;
import com.loopers.domain.metrics.ProductMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 배치 최적화 Processor.
 * 1. 배치 전체의 eventId를 벌크 조회하여 이미 처리된 건 필터링
 * 2. 메모리에서 productId별 집계
 * 3. 벌크 upsert 1회
 * 4. event_handled + event_log 벌크 INSERT
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogBatchProcessor {

    private final ProductMetricsService metricsService;
    private final EventHandledRepository eventHandledRepository;
    private final EventLogRepository eventLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @SuppressWarnings("unchecked")
    public void processBatch(List<ConsumerRecord<String, String>> records) {
        // 1단계: 벌크 멱등 확인
        Set<Long> existingEventIds = eventHandledRepository.findExistingIds(
            records.stream()
                .map(this::toEventId)
                .toList()
        );

        // 2단계: 메모리 집계
        CatalogBatchAggregator aggregator = new CatalogBatchAggregator();

        for (ConsumerRecord<String, String> record : records) {
            Long eventId = toEventId(record);
            if (existingEventIds.contains(eventId)) {
                eventLogRepository.save(EventLogModel.skipped(eventId, "BATCH", "catalog-events"));
                continue;
            }

            try {
                Map<String, Object> envelope = parseEnvelope(record.value());
                String eventType = (String) envelope.get("eventType");
                Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");

                switch (eventType) {
                    case "PRODUCT_LIKED", "PRODUCT_UNLIKED" ->
                        aggregator.addLikeEvent(
                            ((Number) payload.get("productId")).longValue(),
                            ((Number) payload.get("currentLikeCount")).longValue(),
                            LocalDateTime.parse((String) envelope.get("occurredAt")),
                            eventId);
                    case "PRODUCT_VIEWED" ->
                        aggregator.addViewEvent(
                            ((Number) payload.get("productId")).longValue(),
                            eventId);
                }
            } catch (Exception e) {
                log.error("[CatalogBatch] 파싱 실패: offset={}", record.offset(), e);
            }
        }

        if (aggregator.isEmpty()) return;

        // 3단계: 벌크 upsert
        aggregator.getLikeDeltas().forEach((productId, delta) ->
            metricsService.upsertLikeCount(productId, delta.likeCount(), delta.occurredAt()));

        aggregator.getViewIncrements().forEach((productId, count) -> {
            for (long i = 0; i < count; i++) {
                metricsService.incrementViewCount(productId);
            }
        });

        // 4단계: event_handled 벌크 INSERT
        aggregator.getProcessedEventIds().forEach(eventId -> {
            eventHandledRepository.save(new EventHandledModel(eventId));
            eventLogRepository.save(EventLogModel.success(eventId, "BATCH", "catalog-events"));
        });

        log.info("[CatalogBatch] likes={}, views={}, total={}",
            aggregator.getLikeDeltas().size(),
            aggregator.getViewIncrements().size(),
            aggregator.getProcessedEventIds().size());
    }

    private long toEventId(ConsumerRecord<String, String> record) {
        try {
            Map<String, Object> envelope = parseEnvelope(record.value());
            if (envelope.containsKey("eventId")) {
                return ((Number) envelope.get("eventId")).longValue();
            }
        } catch (Exception ignored) {}
        return ((long) record.topic().hashCode() * 31 + record.partition()) * 31 + record.offset();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseEnvelope(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("메시지 파싱 실패: " + value, e);
        }
    }
}

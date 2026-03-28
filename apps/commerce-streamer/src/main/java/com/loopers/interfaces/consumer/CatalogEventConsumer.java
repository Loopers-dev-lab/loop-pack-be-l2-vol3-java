package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.idempotency.EventHandled;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.infrastructure.idempotency.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogEventConsumer {

    private static final String DLT_TOPIC = "catalog-events.DLT";
    private static final int MAX_RETRY = 3;

    private final ProductMetricsJpaRepository productMetricsRepository;
    private final EventHandledJpaRepository eventHandledRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "catalog-events",
        groupId = "streamer-catalog",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        for (ConsumerRecord<String, String> record : records) {
            try {
                processWithRetry(record);
            } catch (Exception e) {
                log.error("catalog-events 처리 실패 → DLQ 전송: offset={}, error={}", record.offset(), e.getMessage(), e);
                sendToDlq(record, e);
            }
        }
        ack.acknowledge();
    }

    /**
     * @Version 낙관적 락 충돌 시 최대 MAX_RETRY 재시도.
     * 동시 업데이트로 version이 맞지 않으면 재조회 후 재시도한다.
     */
    private void processWithRetry(ConsumerRecord<String, String> record) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                processRecord(record);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("낙관적 락 충돌 (attempt {}/{}): offset={}", attempt, MAX_RETRY, record.offset());
                if (attempt == MAX_RETRY) {
                    throw e;
                }
            }
        }
    }

    @Transactional
    public void processRecord(ConsumerRecord<String, String> record) {
        String eventId = getHeader(record, "X-Event-Id");
        String eventType = getHeader(record, "X-Event-Type");

        if (eventId == null || eventType == null) {
            log.warn("이벤트 헤더 누락: offset={}", record.offset());
            return;
        }

        // 멱등 체크
        if (eventHandledRepository.existsById(eventId)) {
            log.debug("이미 처리된 이벤트: eventId={}", eventId);
            return;
        }

        JsonNode envelope = parsePayload(record.value());
        if (envelope == null) return;

        JsonNode data = envelope.get("data");

        switch (eventType) {
            case "LIKED" -> handleLiked(data);
            case "UNLIKED" -> handleUnliked(data);
            case "PRODUCT_VIEWED" -> handleProductViewed(data);
            default -> log.warn("알 수 없는 이벤트 타입: {}", eventType);
        }

        eventHandledRepository.save(new EventHandled(eventId, eventType));
    }

    private void handleLiked(JsonNode data) {
        Long productId = data.get("productId").asLong();
        ProductMetrics metrics = getOrCreateMetrics(productId);
        metrics.incrementLikeCount();
        productMetricsRepository.save(metrics);
        log.info("좋아요 집계: productId={}", productId);
    }

    private void handleUnliked(JsonNode data) {
        Long productId = data.get("productId").asLong();
        ProductMetrics metrics = getOrCreateMetrics(productId);
        metrics.decrementLikeCount();
        productMetricsRepository.save(metrics);
        log.info("좋아요 취소 집계: productId={}", productId);
    }

    private void handleProductViewed(JsonNode data) {
        Long productId = data.get("productId").asLong();
        ProductMetrics metrics = getOrCreateMetrics(productId);
        metrics.incrementViewCount();
        productMetricsRepository.save(metrics);
        log.debug("조회수 집계: productId={}", productId);
    }

    private ProductMetrics getOrCreateMetrics(Long productId) {
        return productMetricsRepository.findById(productId)
            .orElseGet(() -> new ProductMetrics(productId));
    }

    private String getHeader(ConsumerRecord<?, ?> record, String key) {
        var header = record.headers().lastHeader(key);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("payload 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private void sendToDlq(ConsumerRecord<String, String> record, Exception exception) {
        try {
            ProducerRecord<Object, Object> producerRecord = new ProducerRecord<>(
                DLT_TOPIC, null, record.key(), record.value()
            );
            // 원본 헤더 복사
            record.headers().forEach(h -> producerRecord.headers().add(h));
            // 에러 정보 추가
            producerRecord.headers().add(new RecordHeader("X-Error-Message",
                exception.getMessage().getBytes(StandardCharsets.UTF_8)));
            producerRecord.headers().add(new RecordHeader("X-Original-Topic",
                record.topic().getBytes(StandardCharsets.UTF_8)));
            kafkaTemplate.send(producerRecord);
            log.info("DLQ 전송 완료: topic={}, offset={}", DLT_TOPIC, record.offset());
        } catch (Exception e) {
            log.error("DLQ 전송 실패: offset={}, error={}", record.offset(), e.getMessage());
        }
    }
}

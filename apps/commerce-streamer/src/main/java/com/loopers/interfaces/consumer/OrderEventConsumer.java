package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.idempotency.EventHandled;
import com.loopers.domain.metrics.ProductMetricsDaily;
import com.loopers.domain.metrics.ProductMetricsDailyId;
import com.loopers.domain.ranking.RankingKeyGenerator;
import com.loopers.domain.ranking.RankingScoreCalculator;
import com.loopers.infrastructure.idempotency.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsDailyJpaRepository;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderEventConsumer {

    private static final String DLT_TOPIC = "order-events.DLT";

    private final EventHandledJpaRepository eventHandledRepository;
    private final ProductMetricsDailyJpaRepository productMetricsDailyRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RankingScoreCalculator rankingScoreCalculator;
    private final RankingRedisRepository rankingRedisRepository;

    @KafkaListener(
        topics = "order-events",
        groupId = "streamer-order",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        for (ConsumerRecord<String, String> record : records) {
            try {
                processRecord(record);
            } catch (Exception e) {
                log.error("order-events 처리 실패 → DLQ 전송: offset={}, error={}", record.offset(), e.getMessage(), e);
                sendToDlq(record, e);
            }
        }
        ack.acknowledge();
    }

    @Transactional
    public void processRecord(ConsumerRecord<String, String> record) {
        String eventId = getHeader(record, "X-Event-Id");
        String eventType = getHeader(record, "X-Event-Type");

        if (eventId == null || eventType == null) {
            log.warn("이벤트 헤더 누락: offset={}", record.offset());
            return;
        }

        if (eventHandledRepository.existsById(eventId)) {
            log.debug("이미 처리된 이벤트: eventId={}", eventId);
            return;
        }

        JsonNode envelope = parsePayload(record.value());
        if (envelope == null) return;

        JsonNode data = envelope.get("data");

        switch (eventType) {
            case "ORDER_PLACED" -> handleOrderPlaced(data);
            case "PAYMENT_COMPLETED" -> handlePaymentCompleted(data);
            default -> log.warn("알 수 없는 이벤트 타입: {}", eventType);
        }

        eventHandledRepository.save(new EventHandled(eventId, eventType));
    }

    private void handleOrderPlaced(JsonNode data) {
        Long orderId = data.get("orderId").asLong();
        Long userId = data.get("userId").asLong();
        Long totalAmount = data.get("totalAmountValue").asLong();
        log.info("주문 이벤트 수신: orderId={}, userId={}, totalAmount={}", orderId, userId, totalAmount);

        JsonNode items = data.get("items");
        if (items != null && items.isArray()) {
            String rankingKey = RankingKeyGenerator.todayKey();
            for (JsonNode item : items) {
                Long productId = item.get("productId").asLong();
                long price = item.get("price").asLong();
                int quantity = item.get("quantity").asInt();
                double score = rankingScoreCalculator.orderScore(price, quantity);
                rankingRedisRepository.incrementScore(rankingKey, productId, score);

                var dailyId = new ProductMetricsDailyId(productId, LocalDate.now());
                var daily = productMetricsDailyRepository.findById(dailyId)
                    .orElseGet(() -> new ProductMetricsDaily(productId, LocalDate.now()));
                daily.incrementSaleCount(quantity);
                productMetricsDailyRepository.save(daily);
                log.info("주문 랭킹 + 일별 메트릭 반영: productId={}, score={}, quantity={}", productId, score, quantity);
            }
        }
    }

    private void handlePaymentCompleted(JsonNode data) {
        Long paymentId = data.get("paymentId").asLong();
        Long orderId = data.get("orderId").asLong();
        boolean success = data.get("success").asBoolean();
        log.info("결제 완료 이벤트 수신: paymentId={}, orderId={}, success={}", paymentId, orderId, success);
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
            record.headers().forEach(h -> producerRecord.headers().add(h));
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

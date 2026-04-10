package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.BucketTimeUtils;
import com.loopers.application.metrics.ConsumerMetrics;
import com.loopers.application.metrics.ViewBuffer;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductViewConsumer {

    private static final String GROUP_ID = "ranking-consumer";
    private static final String TOPIC = KafkaTopics.PRODUCT_VIEW_EVENTS;

    private final ViewBuffer buffer;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConsumerMetrics consumerMetrics;

    public static final String CONSUMER_ID = "productViewConsumer";

    @KafkaListener(
            id = CONSUMER_ID,
            topics = KafkaTopics.PRODUCT_VIEW_EVENTS,
            groupId = GROUP_ID,
            containerFactory = KafkaConfig.VIEW_BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records) {
        for (ConsumerRecord<String, byte[]> record : records) {
            try {
                processRecord(record);
            } catch (Exception e) {
                log.warn("view 이벤트 파싱 실패: offset={}, partition={}", record.offset(), record.partition(), e);
                consumerMetrics.recordFailed(TOPIC, GROUP_ID, "product.viewed");
            }
        }
    }

    private void processRecord(ConsumerRecord<String, byte[]> record) throws Exception {
        JsonNode envelope = objectMapper.readTree(record.value());
        JsonNode payload = objectMapper.readTree(envelope.path("payload").asText());

        Long productId = payload.path("productId").asLong();
        String viewerId = payload.path("viewerId").asText();
        Instant occurredAt = Instant.parse(payload.path("occurredAt").asText());

        Instant bucket = BucketTimeUtils.truncate5min(occurredAt);
        String dedupKey = "unique:" + productId + ":" + bucket.toEpochMilli();

        Long added = redisTemplate.opsForSet().add(dedupKey, viewerId);
        redisTemplate.expire(dedupKey, Duration.ofMinutes(10));

        if (added != null && added == 1) {
            TopicPartition tp = new TopicPartition(record.topic(), record.partition());
            buffer.increment(tp, productId, bucket);
        }
    }
}

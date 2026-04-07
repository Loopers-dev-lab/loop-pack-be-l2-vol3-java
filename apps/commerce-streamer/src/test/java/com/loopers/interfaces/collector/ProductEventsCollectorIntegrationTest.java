package com.loopers.interfaces.collector;

import com.loopers.infrastructure.collector.EventHandledJpaRepository;
import com.loopers.infrastructure.collector.ProductMetricsJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "collector.product.topic-name=product-events",
        "collector.order.topic-name=order-events",
        "collector.user.topic-name=user-events",
        "collector.product.dlq-suffix=.DLQ",
        "collector.lightweight-idempotency.redis-ttl-days=14",
        "collector.event-handled-cleanup.enabled=false",
        "collector.event-handled-cleanup.fixed-delay-ms=3600000",
        "collector.event-handled-cleanup.retention-days=14",
        "collector.event-handled-cleanup.batch-size=500",
        "spring.batch.job.enabled=false"
})
@Import(MySqlTestContainersConfig.class)
@EmbeddedKafka(partitions = 1, topics = {
        "product-events", "product-events.DLQ",
        "order-events", "order-events.DLQ",
        "user-events", "user-events.DLQ"
})
class ProductEventsCollectorIntegrationTest {

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Autowired
    private EventHandledJpaRepository eventHandledJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${spring.embedded.kafka.brokers}")
    private String embeddedKafkaBrokers;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("product-events를 수신하면 event_handled와 product_metrics를 반영한다.")
    void consumeEvent_shouldPersistHandledAndMetrics() throws Exception {
        sendLikeEvent("evt-1", Instant.parse("2026-03-26T00:00:01Z"), 101L, "LIKED");

        waitUntil(() -> eventHandledJpaRepository.existsById("evt-1"), 10000);

        assertThat(eventHandledJpaRepository.existsById("evt-1")).isTrue();
        assertThat(productMetricsJpaRepository.findById(101L)).isPresent();
        assertThat(productMetricsJpaRepository.findById(101L).orElseThrow().getLikeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 eventId가 중복 수신되면 멱등하게 한 번만 반영한다.")
    void duplicateEvent_shouldBeIdempotent() throws Exception {
        Instant occurredAt = Instant.parse("2026-03-26T00:00:02Z");
        sendLikeEvent("evt-dup", occurredAt, 201L, "LIKED");
        sendLikeEvent("evt-dup", occurredAt, 201L, "LIKED");

        waitUntil(() -> eventHandledJpaRepository.existsById("evt-dup"), 10000);

        assertThat(productMetricsJpaRepository.findById(201L)).isPresent();
        assertThat(productMetricsJpaRepository.findById(201L).orElseThrow().getLikeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("더 늦게 발생한 이벤트가 먼저 반영되면, 과거 이벤트는 순서 역전 방어로 무시한다.")
    void olderEventAfterNewer_shouldBeIgnoredByOccurredAt() throws Exception {
        sendLikeEvent("evt-new", Instant.parse("2026-03-26T00:00:10Z"), 301L, "LIKED");
        sendLikeEvent("evt-old", Instant.parse("2026-03-26T00:00:05Z"), 301L, "UNLIKED");

        waitUntil(() -> productMetricsJpaRepository.findById(301L).isPresent(), 10000);

        assertThat(productMetricsJpaRepository.findById(301L)).isPresent();
        assertThat(productMetricsJpaRepository.findById(301L).orElseThrow().getLikeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("파싱 불가 메시지는 재시도 후 product-events.DLQ로 이동한다.")
    void invalidEvent_shouldPublishToDlq() throws Exception {
        ProducerRecord<Object, Object> invalid = new ProducerRecord<>("product-events", "401", "{not-json");
        kafkaTemplate.send(invalid).get();

        ConsumerRecord<String, String> dlqRecord = pollSingleRecord("product-events.DLQ");
        assertThat(dlqRecord).isNotNull();
        // DLQ 값은 JsonSerializer를 거치며 base64 문자열로 저장되어 디코딩 후 원문을 검증한다.
        String encodedPayload = dlqRecord.value().replace("\"", "");
        String decodedPayload = new String(Base64.getDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        assertThat(decodedPayload).isEqualTo("\"{not-json\"");
    }

    private void sendLikeEvent(String eventId, Instant occurredAt, Long productId, String action) throws Exception {
        String payload = "{"
                + "\"eventId\":\"" + eventId + "\","
                + "\"eventType\":\"PRODUCT_LIKE_CHANGED\","
                + "\"occurredAt\":\"" + occurredAt + "\","
                + "\"partitionKey\":\"" + productId + "\","
                + "\"data\":{"
                + "\"productId\":" + productId + ","
                + "\"action\":\"" + action + "\""
                + "}"
                + "}";

        ProducerRecord<Object, Object> record = new ProducerRecord<>("product-events", String.valueOf(productId), payload);
        record.headers().add("eventId", eventId.getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", "PRODUCT_LIKE_CHANGED".getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get();
    }

    private ConsumerRecord<String, String> pollSingleRecord(String topic) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlq-test-group", "false", embeddedKafkaBroker);
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBrokers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        DefaultKafkaConsumerFactory<String, String> cf =
                new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer());
        Consumer<String, String> consumer = cf.createConsumer();
        try {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, topic);
            // 로컬 환경에서 리밸런스/컨테이너 기동 지연을 고려해 여유 있게 대기한다.
            return KafkaTestUtils.getSingleRecord(consumer, topic, Duration.ofSeconds(40));
        } finally {
            consumer.close();
        }
    }

    private static void waitUntil(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
    }
}

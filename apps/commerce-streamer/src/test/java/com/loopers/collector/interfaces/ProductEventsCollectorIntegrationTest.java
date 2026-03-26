package com.loopers.collector.interfaces;

import com.loopers.infrastructure.collector.EventHandledJpaRepository;
import com.loopers.infrastructure.collector.ProductMetricsJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "collector.product.topic-name=product-events",
        "spring.batch.job.enabled=false"
})
@Import(MySqlTestContainersConfig.class)
@EmbeddedKafka(partitions = 1, topics = {"product-events"})
class ProductEventsCollectorIntegrationTest {

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Autowired
    private EventHandledJpaRepository eventHandledJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

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

        waitUntil(() -> eventHandledJpaRepository.existsById("evt-old"), 10000);

        assertThat(productMetricsJpaRepository.findById(301L)).isPresent();
        assertThat(productMetricsJpaRepository.findById(301L).orElseThrow().getLikeCount()).isEqualTo(1L);
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

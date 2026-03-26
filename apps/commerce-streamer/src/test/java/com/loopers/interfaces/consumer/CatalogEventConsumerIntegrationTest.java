package com.loopers.interfaces.consumer;

import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.metrics.CatalogEventMessage;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("CatalogEventConsumer 통합 테스트")
class CatalogEventConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private ProductMetricsRepository productMetricsRepository;

    @Autowired
    private EventHandledRepository eventHandledRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() throws InterruptedException {
        // Consumer가 토픽에 할당될 때까지 대기 (리밸런스 완료)
        Thread.sleep(5000);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CatalogEventMessage createMessage(String eventId, String eventType, String aggregateId) {
        return new CatalogEventMessage(eventId, eventType, aggregateId, "{}", ZonedDateTime.now());
    }

    private void waitForConsumer() throws InterruptedException {
        Thread.sleep(8000);
    }

    @Nested
    @DisplayName("Kafka 메시지 수신 → ProductMetrics 업데이트")
    class ConsumeMessages {

        @Test
        @DisplayName("성공: PRODUCT_LIKED 메시지를 수신하면 ProductMetrics의 likesCount가 증가한다")
        void productLiked_increasesLikesCount() throws InterruptedException {
            // Given
            Long productId = 100L;
            String eventId = UUID.randomUUID().toString();
            CatalogEventMessage message = createMessage(eventId, "PRODUCT_LIKED", String.valueOf(productId));

            // When
            kafkaTemplate.send("catalog-events", String.valueOf(productId), message).join();
            waitForConsumer();

            // Then
            Optional<ProductMetrics> metrics = productMetricsRepository.findByProductId(productId);
            assertThat(metrics).isPresent();
            assertThat(metrics.get().getLikesCount()).isEqualTo(1L);
            assertThat(eventHandledRepository.existsByEventId(eventId)).isTrue();
        }

        @Test
        @DisplayName("성공: PRODUCT_UNLIKED 메시지를 수신하면 ProductMetrics의 likesCount가 감소한다")
        void productUnliked_decreasesLikesCount() throws InterruptedException {
            // Given
            Long productId = 200L;
            ProductMetrics metrics = ProductMetrics.create(productId);
            metrics.increaseLikes();
            metrics.increaseLikes();
            productMetricsRepository.save(metrics);

            String eventId = UUID.randomUUID().toString();
            CatalogEventMessage message = createMessage(eventId, "PRODUCT_UNLIKED", String.valueOf(productId));

            // When
            kafkaTemplate.send("catalog-events", String.valueOf(productId), message).join();
            waitForConsumer();

            // Then
            Optional<ProductMetrics> updated = productMetricsRepository.findByProductId(productId);
            assertThat(updated).isPresent();
            assertThat(updated.get().getLikesCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("성공: 동일한 eventId의 메시지를 2번 전송해도 1번만 처리된다 (멱등)")
        void duplicateEvent_processedOnlyOnce() throws InterruptedException {
            // Given
            Long productId = 300L;
            String eventId = UUID.randomUUID().toString();
            CatalogEventMessage message = createMessage(eventId, "PRODUCT_LIKED", String.valueOf(productId));

            // When - 같은 메시지 2번 전송
            kafkaTemplate.send("catalog-events", String.valueOf(productId), message).join();
            waitForConsumer();
            kafkaTemplate.send("catalog-events", String.valueOf(productId), message).join();
            waitForConsumer();

            // Then - likesCount는 1이어야 함
            Optional<ProductMetrics> metrics = productMetricsRepository.findByProductId(productId);
            assertThat(metrics).isPresent();
            assertThat(metrics.get().getLikesCount()).isEqualTo(1L);
        }
    }
}

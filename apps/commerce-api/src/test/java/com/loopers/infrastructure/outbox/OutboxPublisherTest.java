package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.event.EventEnvelope;
import com.loopers.event.KafkaTopics;
import com.loopers.fake.FakeOutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("OutboxPublisher 테스트")
class OutboxPublisherTest {

    private FakeOutboxEventRepository outboxEventRepository;
    private KafkaTemplate<String, EventEnvelope> kafkaTemplate;
    private ObjectMapper objectMapper;
    private OutboxPublisher outboxPublisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        outboxEventRepository = new FakeOutboxEventRepository();
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        outboxPublisher = new OutboxPublisher(outboxEventRepository, kafkaTemplate, objectMapper);
    }

    private OutboxEvent createPendingEvent(String topic, String aggregateId) {
        return outboxEventRepository.save(
            OutboxEvent.create(
                "LIKE_CREATED",
                "PRODUCT",
                aggregateId,
                topic,
                "{\"userId\":1,\"productId\":" + aggregateId + "}"
            )
        );
    }

    @Nested
    @DisplayName("publishPendingEvents")
    class PublishPendingEvents {

        @Test
        @DisplayName("성공 - PENDING 상태 이벤트를 Kafka로 발행하고 SENT로 변경")
        void PENDING_이벤트_발행_성공() {
            // Arrange
            OutboxEvent event = createPendingEvent(KafkaTopics.CATALOG_EVENTS, "100");

            CompletableFuture<SendResult<String, EventEnvelope>> future = CompletableFuture.completedFuture(
                createSendResult(KafkaTopics.CATALOG_EVENTS, 0, 0L)
            );
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

            // Act
            outboxPublisher.publishPendingEvents();

            // Assert
            ArgumentCaptor<ProducerRecord<String, EventEnvelope>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
            verify(kafkaTemplate).send(captor.capture());

            ProducerRecord<String, EventEnvelope> record = captor.getValue();
            assertThat(record.topic()).isEqualTo(KafkaTopics.CATALOG_EVENTS);
            assertThat(record.key()).isEqualTo("100");
            assertThat(record.value().eventType()).isEqualTo("LIKE_CREATED");
            assertThat(record.value().aggregateId()).isEqualTo("100");

            // Outbox 상태가 SENT로 변경되어야 함
            List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(100);
            assertThat(pendingEvents).isEmpty();
        }

        @Test
        @DisplayName("성공 - PENDING 이벤트가 없으면 아무 것도 하지 않음")
        void PENDING_이벤트_없음() {
            // Act
            outboxPublisher.publishPendingEvents();

            // Assert
            verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        }

        @Test
        @DisplayName("실패 - Kafka 발행 실패 시 retry count 증가")
        void Kafka_발행_실패_시_retry_증가() {
            // Arrange
            OutboxEvent event = createPendingEvent(KafkaTopics.CATALOG_EVENTS, "100");

            CompletableFuture<SendResult<String, EventEnvelope>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Kafka error"));
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture);

            // Act
            outboxPublisher.publishPendingEvents();

            // Assert - 여전히 PENDING 상태이고 retry count 증가
            List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(100);
            assertThat(pendingEvents).hasSize(1);
            assertThat(pendingEvents.get(0).getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("성공 - 여러 이벤트를 순차적으로 발행")
        void 여러_이벤트_순차_발행() {
            // Arrange
            createPendingEvent(KafkaTopics.CATALOG_EVENTS, "100");
            createPendingEvent(KafkaTopics.CATALOG_EVENTS, "101");
            createPendingEvent(KafkaTopics.ORDER_EVENTS, "1");

            CompletableFuture<SendResult<String, EventEnvelope>> future = CompletableFuture.completedFuture(
                createSendResult(KafkaTopics.CATALOG_EVENTS, 0, 0L)
            );
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

            // Act
            outboxPublisher.publishPendingEvents();

            // Assert
            verify(kafkaTemplate, times(3)).send(any(ProducerRecord.class));
            List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(100);
            assertThat(pendingEvents).isEmpty();
        }
    }

    private SendResult<String, EventEnvelope> createSendResult(String topic, int partition, long offset) {
        RecordMetadata metadata = new RecordMetadata(
            new TopicPartition(topic, partition),
            offset,
            0,
            System.currentTimeMillis(),
            0,
            0
        );
        return new SendResult<>(null, metadata);
    }
}

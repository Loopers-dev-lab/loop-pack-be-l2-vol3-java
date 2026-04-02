package com.loopers.infrastructure.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.DomainEvents;
import com.loopers.domain.queue.JoinQueueResult;
import com.loopers.domain.queue.WaitingQueueService;
import com.loopers.infrastructure.metrics.QueueInfrastructureMetrics;
import com.loopers.support.error.CoreException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KafkaQueueJoinFallbackPublisher}·{@link QueueJoinFallbackKafkaListener}에 연동된
 * {@link QueueInfrastructureMetrics} 카운터 동작을 한 파일에서 검증한다.
 */
@DisplayName("대기열 Kafka 폴백(발행·소비) 메트릭")
class QueueJoinFallbackKafkaMetricsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("발행자(KafkaQueueJoinFallbackPublisher)")
    @ExtendWith(MockitoExtension.class)
    @SuppressWarnings("unchecked")
    class Publisher {

        private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        private final QueueInfrastructureMetrics metrics = new QueueInfrastructureMetrics(meterRegistry);

        @Mock
        private KafkaTemplate<Object, Object> kafkaTemplate;

        @DisplayName("Kafka 발행 성공 시 published 메트릭만 증가한다.")
        @Test
        void publish_whenSendSucceeds_shouldRecordPublishedOnly() {
            CompletableFuture<SendResult<Object, Object>> future =
                    CompletableFuture.completedFuture(mock(SendResult.class));
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

            KafkaQueueJoinFallbackPublisher publisher = new KafkaQueueJoinFallbackPublisher(
                    kafkaTemplate,
                    objectMapper,
                    metrics,
                    "queue-join-fallback",
                    5000L
            );

            publisher.publish("default", 1L, 100L, "req-1");

            verify(kafkaTemplate).send(any(ProducerRecord.class));
            assertThat(meterRegistry.counter("loopers.queue.join.fallback.kafka.published").count()).isEqualTo(1.0);
            assertThat(meterRegistry.counter("loopers.queue.join.fallback.kafka.publish.failed").count()).isZero();
        }

        @DisplayName("Kafka 발행 실패 시 publish.failed 메트릭이 증가하고 CoreException을 던진다.")
        @Test
        void publish_whenSendFails_shouldRecordPublishFailed() {
            CompletableFuture<SendResult<Object, Object>> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("boom"));
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

            KafkaQueueJoinFallbackPublisher publisher = new KafkaQueueJoinFallbackPublisher(
                    kafkaTemplate,
                    objectMapper,
                    metrics,
                    "queue-join-fallback",
                    5000L
            );

            assertThatThrownBy(() -> publisher.publish("default", 1L, 100L, "req-2"))
                    .isInstanceOf(CoreException.class);

            assertThat(meterRegistry.counter("loopers.queue.join.fallback.kafka.publish.failed").count()).isEqualTo(1.0);
            assertThat(meterRegistry.counter("loopers.queue.join.fallback.kafka.published").count()).isZero();
        }
    }

    @Nested
    @DisplayName("소비자(QueueJoinFallbackKafkaListener)")
    @ExtendWith(MockitoExtension.class)
    class Listener {

        private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        private final QueueInfrastructureMetrics metrics = new QueueInfrastructureMetrics(meterRegistry);

        @Mock
        private WaitingQueueService waitingQueueService;

        @DisplayName("복구 성공 시 recovered 메트릭이 증가한다.")
        @Test
        void onMessage_whenRecoverySucceeds_shouldRecordRecovered() throws Exception {
            QueueJoinFallbackKafkaListener listener = new QueueJoinFallbackKafkaListener(
                    waitingQueueService,
                    objectMapper,
                    metrics
            );

            String json = """
                    {
                      "eventId": "req-1",
                      "eventType": "%s",
                      "data": {
                        "eventId": "default",
                        "userId": 1,
                        "score": 1000
                      }
                    }
                    """
                    .formatted(DomainEvents.Type.QUEUE_JOIN_FALLBACK_REQUESTED);

            ConsumerRecord<Object, Object> record = new ConsumerRecord<>("queue-join-fallback", 0, 0L, "1", json);
            Acknowledgment ack = mock(Acknowledgment.class);

            when(waitingQueueService.joinQueueFromRecovery("default", 1L, 1000L))
                    .thenReturn(JoinQueueResult.synced(0L, 1L));

            listener.onMessage(record, ack);

            verify(waitingQueueService).joinQueueFromRecovery("default", 1L, 1000L);
            verify(ack).acknowledge();
            assertThat(meterRegistry.counter("loopers.queue.join.fallback.recovered").count()).isEqualTo(1.0);
        }

        @DisplayName("DLT 핸들러 호출 시 dlt 메트릭이 증가한다.")
        @Test
        void onDlt_shouldRecordDlt() {
            QueueJoinFallbackKafkaListener listener = new QueueJoinFallbackKafkaListener(
                    waitingQueueService,
                    objectMapper,
                    metrics
            );

            ConsumerRecord<Object, Object> record = new ConsumerRecord<>("queue-join-fallback", 0, 0L, "1", "{}");
            Acknowledgment ack = mock(Acknowledgment.class);

            listener.onDlt(record, ack);

            verify(ack).acknowledge();
            assertThat(meterRegistry.counter("loopers.queue.join.fallback.dlt").count()).isEqualTo(1.0);
        }

        @DisplayName("eventType이 QUEUE_JOIN_FALLBACK_REQUESTED가 아니면 join 없이 ack만 한다.")
        @Test
        void onMessage_whenEventTypeMismatch_shouldAckWithoutRecovery() {
            QueueJoinFallbackKafkaListener listener = new QueueJoinFallbackKafkaListener(
                    waitingQueueService,
                    objectMapper,
                    metrics
            );

            String json = """
                    {
                      "eventId": "x",
                      "eventType": "OTHER_EVENT",
                      "data": { "eventId": "default", "userId": 1, "score": 1 }
                    }
                    """;
            ConsumerRecord<Object, Object> record = new ConsumerRecord<>("queue-join-fallback", 0, 0L, "1", json);
            Acknowledgment ack = mock(Acknowledgment.class);

            listener.onMessage(record, ack);

            verify(waitingQueueService, never()).joinQueueFromRecovery(anyString(), anyLong(), anyLong());
            verify(ack).acknowledge();
            assertThat(meterRegistry.counter("loopers.queue.join.fallback.recovered").count()).isZero();
        }

        @DisplayName("envelope JSON이 깨지면 IllegalArgumentException (ack 전)")
        @Test
        void onMessage_whenPayloadInvalidJson_shouldThrowBeforeAck() {
            QueueJoinFallbackKafkaListener listener = new QueueJoinFallbackKafkaListener(
                    waitingQueueService,
                    objectMapper,
                    metrics
            );

            ConsumerRecord<Object, Object> record = new ConsumerRecord<>("queue-join-fallback", 0, 0L, "1", "{ not-json");
            Acknowledgment ack = mock(Acknowledgment.class);

            assertThatThrownBy(() -> listener.onMessage(record, ack))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invalid queue join fallback envelope");

            verify(ack, never()).acknowledge();
            verify(waitingQueueService, never()).joinQueueFromRecovery(anyString(), anyLong(), anyLong());
        }
    }
}

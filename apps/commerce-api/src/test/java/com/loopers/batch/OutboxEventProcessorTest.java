package com.loopers.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxEventStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventProcessor 단위 테스트")
class OutboxEventProcessorTest {

    @Mock
    OutboxEventRepository outboxRepository;

    @Mock
    KafkaTemplate<Object, Object> kafkaTemplate;

    @Spy
    ObjectMapper objectMapper;

    @InjectMocks
    OutboxEventProcessor outboxEventProcessor;

    private OutboxEventModel createTestEvent() {
        OutboxEventModel event = OutboxEventModel.create(
                "ORDER", "1", "ORDER_CREATED",
                "order-events", "order-1",
                "{\"orderId\":1,\"items\":[]}"
        );
        ReflectionTestUtils.setField(event, "eventId", 1L);
        return event;
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<Object, Object>> successFuture() {
        SendResult<Object, Object> sendResult = new SendResult<>(
                new ProducerRecord<>("order-events", "order-1", "{}"),
                new RecordMetadata(new TopicPartition("order-events", 0), 0, 0, 0, 0, 0)
        );
        return CompletableFuture.completedFuture(sendResult);
    }

    private CompletableFuture<SendResult<Object, Object>> failedFuture() {
        CompletableFuture<SendResult<Object, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka unavailable"));
        return future;
    }

    @Test
    @DisplayName("Kafka 발행 성공 시 이벤트 상태가 PUBLISHED로 변경된다")
    void publishAndMark_Success_ShouldMarkAsPublished() {
        OutboxEventModel event = createTestEvent();
        doReturn(successFuture()).when(kafkaTemplate).send(any(String.class), any(), any());
        when(outboxRepository.save(any(OutboxEventModel.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = outboxEventProcessor.publishAndMark(event);

        assertThat(result).isTrue();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        verify(outboxRepository).save(event);
    }

    @Test
    @DisplayName("Kafka 발행 실패 시 retryCount가 1 증가하고 FAILED 상태로 변경된다")
    void publishAndMark_Failure_ShouldIncrementRetryCount() {
        OutboxEventModel event = createTestEvent();
        doReturn(failedFuture()).when(kafkaTemplate).send(any(String.class), any(), any());
        when(outboxRepository.save(any(OutboxEventModel.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = outboxEventProcessor.publishAndMark(event);

        assertThat(result).isFalse();
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        verify(outboxRepository).save(event);
    }

    @Test
    @DisplayName("5회 실패 시 DEAD 상태로 변경된다")
    void publishAndMark_MaxRetry_ShouldMarkAsDead() {
        OutboxEventModel event = createTestEvent();
        doAnswer(inv -> failedFuture()).when(kafkaTemplate).send(any(String.class), any(), any());
        when(outboxRepository.save(any(OutboxEventModel.class))).thenAnswer(inv -> inv.getArgument(0));

        for (int i = 0; i < 5; i++) {
            outboxEventProcessor.publishAndMark(event);
        }

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.DEAD);
        assertThat(event.getRetryCount()).isEqualTo(5);
    }
}

package com.loopers.infrastructure.outbox.kafka;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.loopers.domain.outbox.OutboxEvent;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxEventProducerTest {

    @InjectMocks
    private KafkaOutboxEventProducer kafkaOutboxEventProducer;

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Mock
    private SendResult<Object, Object> sendResult;

    @Mock
    private Runnable onSuccess;

    @Mock
    private Consumer<Throwable> onFailure;

    @DisplayName("Kafka 이벤트를 발행할 때,")
    @Nested
    class ProduceEvent {

        @DisplayName("발행에 성공하면, onSuccess 콜백을 실행한다.")
        @Test
        void callsOnSuccess_whenSendSucceeds() {
            // arrange
            OutboxEvent event = createEvent();
            given(kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload()))
                    .willReturn(CompletableFuture.completedFuture(sendResult));

            // act
            kafkaOutboxEventProducer.produceEvent(event, onSuccess, onFailure);

            // assert
            then(onSuccess).should().run();
            then(onFailure).should(never()).accept(org.mockito.ArgumentMatchers.any());
        }

        @DisplayName("발행에 실패하면, onFailure 콜백을 실행한다.")
        @Test
        void callsOnFailure_whenSendFails() {
            // arrange
            OutboxEvent event = createEvent();
            RuntimeException exception = new RuntimeException("broker down");
            given(kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload()))
                    .willReturn(CompletableFuture.failedFuture(exception));

            // act
            kafkaOutboxEventProducer.produceEvent(event, onSuccess, onFailure);

            // assert
            then(onFailure).should().accept(exception);
            then(onSuccess).should(never()).run();
        }
    }

    private OutboxEvent createEvent() {
        return OutboxEvent.create(
                UUID.randomUUID(),
                1L,
                "LIKE",
                "LIKED",
                "{\"productId\":1}",
                "like-liked-v1",
                "1",
                1L
        );
    }
}

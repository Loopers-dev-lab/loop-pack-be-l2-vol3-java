package com.loopers.infrastructure.outbox.relay;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.apache.kafka.common.errors.RecordTooLargeException;
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
import com.loopers.domain.outbox.OutboxEventService;

@ExtendWith(MockitoExtension.class)
class OutboxEventRelayTest {

    @InjectMocks
    private OutboxEventRelay outboxEventRelay;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Mock
    private SendResult<Object, Object> sendResult;

    private OutboxEvent createEvent(Long id) {
        OutboxEvent event = OutboxEvent.create(
                1L,
                "LIKE",
                "LIKED",
                "{\"productId\":1}",
                "like-liked-v1",
                "1",
                1L
        );
        try {
            var idField = OutboxEvent.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(event, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return event;
    }

    @DisplayName("Relay를 실행할 때,")
    @Nested
    class Relay {

        @DisplayName("INIT 이벤트가 있으면, Kafka에 발행하고 성공 처리한다.")
        @Test
        void publishesAndSucceeds() {
            // arrange
            OutboxEvent event = createEvent(1L);
            given(outboxEventService.findPendingEvents(100)).willReturn(List.of(event));
            given(kafkaTemplate.send("like-liked-v1", "1", "{\"productId\":1}"))
                    .willReturn(CompletableFuture.completedFuture(sendResult));

            // act
            outboxEventRelay.relay();

            // assert
            then(outboxEventService).should().publish(1L);
        }

        @DisplayName("재시도 가능한 실패 시, fail 처리하고 나머지를 계속 처리한다.")
        @Test
        void failsAndContinuesOnRetryableError() {
            // arrange
            OutboxEvent event1 = createEvent(1L);
            OutboxEvent event2 = createEvent(2L);
            OutboxEvent failedEvent = createEvent(1L);
            given(outboxEventService.findPendingEvents(100)).willReturn(List.of(event1, event2));
            given(kafkaTemplate.send(eq("like-liked-v1"), eq("1"), eq("{\"productId\":1}")))
                    .willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
            given(outboxEventService.publishFail(anyLong())).willReturn(failedEvent);

            // act
            outboxEventRelay.relay();

            // assert
            then(outboxEventService).should().publishFail(1L);
            then(outboxEventService).should().publishFail(2L);
            then(outboxEventService).should(never()).dead(anyLong());
        }

        @DisplayName("재시도 불가능한 실패 시, 즉시 dead 처리한다.")
        @Test
        void deadsOnNonRetryableError() {
            // arrange
            OutboxEvent event = createEvent(1L);
            given(outboxEventService.findPendingEvents(100)).willReturn(List.of(event));
            given(kafkaTemplate.send("like-liked-v1", "1", "{\"productId\":1}"))
                    .willReturn(CompletableFuture.failedFuture(
                            new CompletionException(new RecordTooLargeException("too large"))));

            // act
            outboxEventRelay.relay();

            // assert
            then(outboxEventService).should().dead(1L);
            then(outboxEventService).should(never()).publishFail(anyLong());
        }

        @DisplayName("INIT 이벤트가 없으면, Kafka에 발행하지 않는다.")
        @Test
        void doesNothingWhenNoEvents() {
            // arrange
            given(outboxEventService.findPendingEvents(100)).willReturn(List.of());

            // act
            outboxEventRelay.relay();

            // assert
            then(kafkaTemplate).shouldHaveNoInteractions();
        }
    }
}

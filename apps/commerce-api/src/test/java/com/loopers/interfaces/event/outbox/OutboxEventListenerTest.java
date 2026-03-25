package com.loopers.interfaces.event.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;

import java.util.Collections;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.order.OrderEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventProducer;
import com.loopers.domain.outbox.OutboxEventService;
import com.loopers.domain.outbox.OutboxEventWriter;

@ExtendWith(MockitoExtension.class)
class OutboxEventListenerTest {

    @InjectMocks
    private OutboxEventListener outboxEventListener;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private OutboxEventProducer outboxEventProducer;

    @DisplayName("BEFORE_COMMIT: Outbox 저장할 때,")
    @Nested
    class SaveOutbox {

        @DisplayName("OutboxEventWriter에 이벤트를 기록한다.")
        @Test
        void writesToOutbox() {
            // arrange
            UUID eventId = UUID.randomUUID();
            OrderEvent.OrderCompleted event = new OrderEvent.OrderCompleted(eventId, 1L, Collections.emptyList());

            // act
            outboxEventListener.saveOutbox(event);

            // assert
            then(outboxEventWriter).should().write(
                    eventId, 1L, "ORDER", "ORDER_COMPLETED",
                    event, "order-completed-v1", "1"
            );
        }
    }

    @DisplayName("AFTER_COMMIT: Kafka 발행할 때,")
    @Nested
    class PublishToKafka {

        @DisplayName("선점에 성공하면, produceEvent를 호출한다.")
        @Test
        void producesEvent_whenClaimSucceeds() {
            // arrange
            UUID eventId = UUID.randomUUID();
            OutboxEvent outboxEvent = createOutboxEvent(eventId);
            OrderEvent.OrderCompleted event = new OrderEvent.OrderCompleted(eventId, 1L, Collections.emptyList());

            given(outboxEventService.publish(eventId)).willReturn(true);
            given(outboxEventService.findById(eventId)).willReturn(outboxEvent);

            // act
            outboxEventListener.publishToKafka(event);

            // assert
            then(outboxEventProducer).should().produceEvent(any(OutboxEvent.class), any(Runnable.class), any(Consumer.class));
        }

        @DisplayName("선점에 실패하면, produceEvent를 호출하지 않는다.")
        @Test
        void skips_whenClaimFails() {
            // arrange
            UUID eventId = UUID.randomUUID();
            OrderEvent.OrderCompleted event = new OrderEvent.OrderCompleted(eventId, 1L, Collections.emptyList());

            given(outboxEventService.publish(eventId)).willReturn(false);

            // act
            outboxEventListener.publishToKafka(event);

            // assert
            then(outboxEventProducer).shouldHaveNoInteractions();
        }

        @DisplayName("발행에 실패하면, publishFail을 호출한다.")
        @Test
        void callsPublishFail_whenProduceFails() {
            // arrange
            UUID eventId = UUID.randomUUID();
            OutboxEvent outboxEvent = createOutboxEvent(eventId);
            OutboxEvent failedEvent = createOutboxEvent(eventId);
            OrderEvent.OrderCompleted event = new OrderEvent.OrderCompleted(eventId, 1L, Collections.emptyList());

            given(outboxEventService.publish(eventId)).willReturn(true);
            given(outboxEventService.findById(eventId)).willReturn(outboxEvent);
            given(outboxEventService.publishFail(eventId)).willReturn(failedEvent);

            doAnswer(invocation -> {
                Consumer<Throwable> onFailure = invocation.getArgument(2);
                onFailure.accept(new RuntimeException("broker down"));
                return null;
            }).when(outboxEventProducer).produceEvent(any(OutboxEvent.class), any(), any());

            // act
            outboxEventListener.publishToKafka(event);

            // assert
            then(outboxEventService).should().publishFail(eventId);
        }

        @DisplayName("발행에 성공하면, publishFail을 호출하지 않는다.")
        @Test
        void doesNotCallPublishFail_whenProduceSucceeds() {
            // arrange
            UUID eventId = UUID.randomUUID();
            OutboxEvent outboxEvent = createOutboxEvent(eventId);
            OrderEvent.OrderCompleted event = new OrderEvent.OrderCompleted(eventId, 1L, Collections.emptyList());

            given(outboxEventService.publish(eventId)).willReturn(true);
            given(outboxEventService.findById(eventId)).willReturn(outboxEvent);

            doAnswer(invocation -> {
                Runnable onSuccess = invocation.getArgument(1);
                onSuccess.run();
                return null;
            }).when(outboxEventProducer).produceEvent(any(OutboxEvent.class), any(), any());

            // act
            outboxEventListener.publishToKafka(event);

            // assert
            then(outboxEventService).should(never()).publishFail(eventId);
        }
    }

    private OutboxEvent createOutboxEvent(UUID id) {
        return OutboxEvent.create(
                id,
                1L,
                "ORDER",
                "ORDER_COMPLETED",
                "{\"orderId\":1}",
                "order-completed-v1",
                "1",
                1L
        );
    }
}

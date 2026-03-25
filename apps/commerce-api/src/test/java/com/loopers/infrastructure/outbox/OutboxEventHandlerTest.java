package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.LikeEvent;
import com.loopers.domain.order.OrderEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class OutboxEventHandlerTest {

    OutboxEventJpaRepository outboxEventJpaRepository = mock(OutboxEventJpaRepository.class);
    ObjectMapper objectMapper = new ObjectMapper();

    OutboxEventHandler handler = new OutboxEventHandler(outboxEventJpaRepository, objectMapper);

    @DisplayName("좋아요 생성 이벤트 수신 시, ")
    @Nested
    class HandleLikeCreated {

        @DisplayName("catalog-events 토픽에 LIKE_CREATED 이벤트가 저장된다.")
        @Test
        void savesOutboxEvent_whenLikeCreatedEventReceived() {
            // arrange
            LikeEvent.Created event = new LikeEvent.Created(1L, 42L);

            // act
            handler.handle(event);

            // assert
            verify(outboxEventJpaRepository).save(argThat(outboxEvent ->
                    outboxEvent.topic().equals("catalog-events") &&
                    outboxEvent.partitionKey().equals("42") &&
                    outboxEvent.payload().contains("LIKE_CREATED")
            ));
        }
    }

    @DisplayName("좋아요 삭제 이벤트 수신 시, ")
    @Nested
    class HandleLikeDeleted {

        @DisplayName("catalog-events 토픽에 LIKE_DELETED 이벤트가 저장된다.")
        @Test
        void savesOutboxEvent_whenLikeDeletedEventReceived() {
            // arrange
            LikeEvent.Deleted event = new LikeEvent.Deleted(1L, 42L);

            // act
            handler.handle(event);

            // assert
            verify(outboxEventJpaRepository).save(argThat(outboxEvent ->
                    outboxEvent.topic().equals("catalog-events") &&
                    outboxEvent.partitionKey().equals("42") &&
                    outboxEvent.payload().contains("LIKE_DELETED")
            ));
        }
    }

    @DisplayName("주문 생성 이벤트 수신 시, ")
    @Nested
    class HandleOrderCreated {

        @DisplayName("order-events 토픽에 ORDER_CREATED 이벤트가 저장된다.")
        @Test
        void savesOutboxEvent_whenOrderCreatedEventReceived() {
            // arrange
            OrderEvent.Created event = new OrderEvent.Created(1L, "20260325-ABCDEF", 90000L, null);

            // act
            handler.handle(event);

            // assert
            verify(outboxEventJpaRepository).save(argThat(outboxEvent ->
                    outboxEvent.topic().equals("order-events") &&
                    outboxEvent.partitionKey().equals("20260325-ABCDEF") &&
                    outboxEvent.payload().contains("ORDER_CREATED")
            ));
        }
    }
}

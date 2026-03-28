package com.loopers.application.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayUnitTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @InjectMocks
    private OutboxRelay outboxRelay;

    @DisplayName("미발행 이벤트를 Kafka로 발행할 때,")
    @Nested
    class PublishPendingEvents {

        @DisplayName("PRODUCT 타입이면, catalog-events 토픽으로 발행하고 published=true로 변경한다")
        @Test
        void publishProductEventSuccess() {
            // given
            OutboxEvent event = new OutboxEvent("PRODUCT", 10L, "PRODUCT_LIKED",
                    "{\"memberId\":1,\"productId\":10}");
            when(outboxEventRepository.findUnpublishedEvents()).thenReturn(List.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            // when
            outboxRelay.publishPendingEvents();

            // then
            verify(kafkaTemplate).send(eq("catalog-events"), eq("10"), any(Map.class));
            assertThat(event.isPublished()).isTrue();
        }

        @DisplayName("ORDER 타입이면, order-events 토픽으로 발행한다")
        @Test
        void publishOrderEventSuccess() {
            // given
            OutboxEvent event = new OutboxEvent("ORDER", 100L, "ORDER_CREATED", "{}");
            when(outboxEventRepository.findUnpublishedEvents()).thenReturn(List.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            // when
            outboxRelay.publishPendingEvents();

            // then
            verify(kafkaTemplate).send(eq("order-events"), eq("100"), any(Map.class));
            assertThat(event.isPublished()).isTrue();
        }

        @DisplayName("미발행 이벤트가 없으면, Kafka 발행이 호출되지 않는다")
        @Test
        void noEventsToPublish() {
            // given
            when(outboxEventRepository.findUnpublishedEvents()).thenReturn(List.of());

            // when
            outboxRelay.publishPendingEvents();

            // then
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }
    }
}

package com.loopers.application.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxRelaySchedulerTest {

    private OutboxEventRepository outboxEventRepository;
    private KafkaTemplate<Object, Object> kafkaTemplate;
    private OutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        scheduler = new OutboxRelayScheduler(outboxEventRepository, kafkaTemplate);
    }

    @DisplayName("미발행 이벤트를 Kafka로 발행하고 published로 마킹한다")
    @Test
    void relay_publishesAndMarks() {
        OutboxEvent event = OutboxEvent.create("PRODUCT", 10L, "PRODUCT_LIKED", "{\"productId\":10}");
        when(outboxEventRepository.findUnpublished(anyInt())).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        scheduler.relay();

        verify(kafkaTemplate).send(eq("catalog.events.topic-v1"), eq("10"), anyString());
        assertThat(event.isPublished()).isTrue();
        verify(outboxEventRepository).save(event);
    }

    @DisplayName("ORDER 타입 이벤트는 order 토픽으로 발행한다")
    @Test
    void relay_orderEvent_sendsToOrderTopic() {
        OutboxEvent event = OutboxEvent.create("ORDER", 1L, "ORDER_CANCELLED", "{\"orderId\":1}");
        when(outboxEventRepository.findUnpublished(anyInt())).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        scheduler.relay();

        verify(kafkaTemplate).send(eq("order.events.topic-v1"), eq("1"), anyString());
        assertThat(event.isPublished()).isTrue();
    }

    @DisplayName("COUPON 타입 이벤트는 coupon issue 토픽으로 발행한다")
    @Test
    void relay_couponEvent_sendsToCouponTopic() {
        OutboxEvent event = OutboxEvent.create("COUPON", 1L, "COUPON_ISSUE_REQUESTED", "{\"requestId\":1}");
        when(outboxEventRepository.findUnpublished(anyInt())).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        scheduler.relay();

        verify(kafkaTemplate).send(eq("coupon.issue-requests.topic-v1"), eq("1"), anyString());
        assertThat(event.isPublished()).isTrue();
    }

    @DisplayName("미발행 이벤트가 없으면 Kafka 발행을 하지 않는다")
    @Test
    void relay_noEvents_doesNothing() {
        when(outboxEventRepository.findUnpublished(anyInt())).thenReturn(List.of());

        scheduler.relay();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @DisplayName("Kafka 발행 실패 시 해당 이벤트를 건너뛰고 나머지를 처리한다")
    @Test
    void relay_kafkaFailure_skipsAndContinues() {
        OutboxEvent event1 = OutboxEvent.create("PRODUCT", 10L, "PRODUCT_LIKED", "{\"productId\":10}");
        OutboxEvent event2 = OutboxEvent.create("PRODUCT", 20L, "PRODUCT_LIKED", "{\"productId\":20}");
        when(outboxEventRepository.findUnpublished(anyInt())).thenReturn(List.of(event1, event2));
        when(kafkaTemplate.send(anyString(), eq("10"), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka 장애")));
        when(kafkaTemplate.send(anyString(), eq("20"), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        scheduler.relay();

        assertThat(event1.isPublished()).isFalse();
        assertThat(event2.isPublished()).isTrue();
    }
}

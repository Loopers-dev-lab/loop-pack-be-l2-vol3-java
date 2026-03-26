package com.loopers.application.outbox;

import com.loopers.domain.outbox.CatalogEventMessage;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventService;
import com.loopers.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPollingScheduler 단위 테스트")
class OutboxPollingSchedulerTest {

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @InjectMocks
    private OutboxPollingScheduler outboxPollingScheduler;

    private void stubbingKafkaSend(CompletableFuture<SendResult<Object, Object>> future) {
        doReturn(future).when(kafkaTemplate).send(anyString(), any(Object.class), any(Object.class));
    }

    @Nested
    @DisplayName("pollAndPublish - Outbox 폴링 및 Kafka 발행")
    class PollAndPublish {

        @Test
        @DisplayName("성공: INIT 이벤트를 Kafka로 발행하고 SENT로 변경한다")
        void pollAndPublish_sendsToKafkaAndMarksSent() {
            // Given
            OutboxEvent event = OutboxEvent.create("catalog-events", "PRODUCT_LIKED", "123", "{\"userId\":1,\"productId\":123}");
            given(outboxEventService.findPendingEvents(anyInt())).willReturn(List.of(event));
            stubbingKafkaSend(CompletableFuture.completedFuture(null));

            // When
            outboxPollingScheduler.pollAndPublish();

            // Then
            ArgumentCaptor<Object> keyCaptor = ArgumentCaptor.forClass(Object.class);
            ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
            then(kafkaTemplate).should().send(eq("catalog-events"), keyCaptor.capture(), valueCaptor.capture());
            assertThat(keyCaptor.getValue()).isEqualTo("123");
            assertThat(valueCaptor.getValue()).isInstanceOf(CatalogEventMessage.class);
            then(outboxEventService).should().markAsSent(event);
        }

        @Test
        @DisplayName("성공: Kafka 발행 실패 시 상태를 유지한다")
        void pollAndPublish_failureKeepsInitStatus() {
            // Given
            OutboxEvent event = OutboxEvent.create("catalog-events", "PRODUCT_LIKED", "123", "{}");
            given(outboxEventService.findPendingEvents(anyInt())).willReturn(List.of(event));
            stubbingKafkaSend(CompletableFuture.failedFuture(new RuntimeException("Kafka 전송 실패")));

            // When
            outboxPollingScheduler.pollAndPublish();

            // Then
            then(outboxEventService).should(never()).markAsSent(any());
        }

        @Test
        @DisplayName("성공: 발행할 이벤트가 없으면 Kafka를 호출하지 않는다")
        void pollAndPublish_noEvents_doesNothing() {
            // Given
            given(outboxEventService.findPendingEvents(anyInt())).willReturn(Collections.emptyList());

            // When
            outboxPollingScheduler.pollAndPublish();

            // Then
            then(kafkaTemplate).shouldHaveNoInteractions();
        }
    }
}

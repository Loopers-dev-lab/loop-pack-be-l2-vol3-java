package com.loopers.infrastructure.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OutboxEventRelaySchedulerTest {

    OutboxEventJpaRepository outboxEventJpaRepository = mock(OutboxEventJpaRepository.class);

    @SuppressWarnings("unchecked")
    KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);

    OutboxEventRelayScheduler scheduler = new OutboxEventRelayScheduler(outboxEventJpaRepository, kafkaTemplate);

    @DisplayName("relay() 를 호출할 때, ")
    @Nested
    class Relay {

        @DisplayName("미발행 이벤트가 있으면, Kafka 로 발행하고 publishedAt 을 갱신한다.")
        @Test
        void sendsToKafkaAndMarksPublished_whenUnpublishedEventsExist() {
            // arrange
            OutboxEvent event = OutboxEvent.of("uuid-1", "catalog-events", "42", "{\"eventType\":\"LIKE_CREATED\"}");
            when(outboxEventJpaRepository.findTop100ByPublishedAtIsNull()).thenReturn(List.of(event));

            // act
            scheduler.relay();

            // assert
            verify(kafkaTemplate).send("catalog-events", "42", "{\"eventType\":\"LIKE_CREATED\"}");
            assertThat(event.publishedAt()).isNotNull();
        }

        @DisplayName("미발행 이벤트가 없으면, Kafka 발행을 하지 않는다.")
        @Test
        void doesNotSendToKafka_whenNoUnpublishedEvents() {
            // arrange
            when(outboxEventJpaRepository.findTop100ByPublishedAtIsNull()).thenReturn(List.of());

            // act
            scheduler.relay();

            // assert
            verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        }
    }
}

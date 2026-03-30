package com.loopers.infrastructure.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @DisplayName("OutboxEvent 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값으로 생성하면, publishedAt 이 null 이다.")
        @Test
        void createsOutboxEvent_withNullPublishedAt() {
            // arrange & act
            OutboxEvent result = OutboxEvent.of("uuid", "catalog-events", "42", "{}");

            // assert
            assertThat(result).isNotNull();
            assertThat(result.publishedAt()).isNull();
        }
    }

    @DisplayName("markPublished() 를 호출할 때, ")
    @Nested
    class MarkPublished {

        @DisplayName("publishedAt 이 설정된다.")
        @Test
        void setsPublishedAt_whenCalled() {
            // arrange
            OutboxEvent event = OutboxEvent.of("uuid", "catalog-events", "42", "{}");

            // act
            event.markPublished();

            // assert
            assertThat(event.publishedAt()).isNotNull();
        }
    }
}

package com.loopers.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    @DisplayName("Outbox 이벤트를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("필드가 올바르게 설정되고, 상태는 INIT이다.")
        @Test
        void setsFieldsAndStatusInit() {
            // act
            OutboxEvent event = OutboxEvent.create(
                    1L,
                    "LIKE",
                    "LIKED",
                    "{\"productId\":1}",
                    "like-liked-v1",
                    "1",
                    1L
            );

            // assert
            assertAll(
                    () -> assertThat(event.getAggregateId()).isEqualTo(1L),
                    () -> assertThat(event.getAggregateType()).isEqualTo("LIKE"),
                    () -> assertThat(event.getEventType()).isEqualTo("LIKED"),
                    () -> assertThat(event.getPayload()).isEqualTo("{\"productId\":1}"),
                    () -> assertThat(event.getTopic()).isEqualTo("like-liked-v1"),
                    () -> assertThat(event.getPartitionKey()).isEqualTo("1"),
                    () -> assertThat(event.getVersion()).isEqualTo(1L),
                    () -> assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.INIT),
                    () -> assertThat(event.getPublishedAt()).isNull()
            );
        }
    }
}

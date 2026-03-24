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

    @DisplayName("발행 성공 처리할 때,")
    @Nested
    class MarkSuccess {

        @DisplayName("상태가 PUBLISHED로 변경되고, publishedAt이 설정된다.")
        @Test
        void changesStatusAndSetsPublishedAt() {
            // arrange
            OutboxEvent event = OutboxEvent.create(
                    1L,
                    "LIKE",
                    "LIKED",
                    "{}",
                    "like-liked-v1",
                    "1",
                    1L
            );

            // act
            event.publish();

            // assert
            assertAll(
                    () -> assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED),
                    () -> assertThat(event.getPublishedAt()).isNotNull()
            );
        }
    }

    @DisplayName("발행 실패 처리할 때,")
    @Nested
    class Fail {

        @DisplayName("상태가 PUBLISH_FAILED로 변경되고, retryCount가 증가한다.")
        @Test
        void changesStatusToFailAndIncrementsRetryCount() {
            // arrange
            OutboxEvent event = OutboxEvent.create(
                    1L,
                    "LIKE",
                    "LIKED",
                    "{}",
                    "like-liked-v1",
                    "1",
                    1L
            );

            // act
            event.publishFail();

            // assert
            assertAll(
                    () -> assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISH_FAILED),
                    () -> assertThat(event.getRetryCount()).isEqualTo(1),
                    () -> assertThat(event.getFailedAt()).isNotNull()
            );
        }

        @DisplayName("재시도 횟수가 상한에 도달하면, DEAD 상태로 전이한다.")
        @Test
        void transitionsToDeadWhenRetryExhausted() {
            // arrange
            OutboxEvent event = OutboxEvent.create(
                    1L,
                    "LIKE",
                    "LIKED",
                    "{}",
                    "like-liked-v1",
                    "1",
                    1L
            );

            // act
            event.publishFail();
            event.publishFail();
            event.publishFail();

            // assert
            assertAll(
                    () -> assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.DEAD),
                    () -> assertThat(event.isDead()).isTrue(),
                    () -> assertThat(event.getRetryCount()).isEqualTo(3)
            );
        }
    }

    @DisplayName("재시도 불가능한 실패 처리할 때,")
    @Nested
    class Dead {

        @DisplayName("즉시 DEAD 상태로 전이한다.")
        @Test
        void transitionsToDeadImmediately() {
            // arrange
            OutboxEvent event = OutboxEvent.create(
                    1L,
                    "LIKE",
                    "LIKED",
                    "{}",
                    "like-liked-v1",
                    "1",
                    1L
            );

            // act
            event.dead();

            // assert
            assertAll(
                    () -> assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.DEAD),
                    () -> assertThat(event.isDead()).isTrue(),
                    () -> assertThat(event.getFailedAt()).isNotNull()
            );
        }
    }
}

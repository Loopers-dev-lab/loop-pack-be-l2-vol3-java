package com.loopers.support.outbox;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OutboxEventTest {

    private OutboxEvent createEvent() {
        return OutboxEvent.create("event-1", "payment.completed", "Order", "1", "{}", "order-events");
    }

    @Nested
    class 생성 {

        @Test
        void 유효한_값이면_PENDING_상태로_생성된다() {
            OutboxEvent event = createEvent();

            assertAll(
                    () -> assertThat(event.getEventId()).isEqualTo("event-1"),
                    () -> assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING),
                    () -> assertThat(event.getCreatedAt()).isNotNull()
            );
        }

        @Test
        void retryCount가_0으로_초기화된다() {
            OutboxEvent event = createEvent();

            assertThat(event.getRetryCount()).isZero();
        }

        @Test
        void sentAt이_null이다() {
            OutboxEvent event = createEvent();

            assertThat(event.getSentAt()).isNull();
        }
    }

    @Nested
    class SENT_마킹 {

        @Test
        void markSent하면_상태가_SENT가_된다() {
            OutboxEvent event = createEvent();

            event.markSent();

            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        }

        @Test
        void markSent하면_sentAt이_설정된다() {
            OutboxEvent event = createEvent();

            event.markSent();

            assertThat(event.getSentAt()).isNotNull();
        }
    }

    @Nested
    class FAILED_마킹 {

        @Test
        void markFailed하면_상태가_FAILED가_된다() {
            OutboxEvent event = createEvent();

            event.markFailed();

            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        }
    }

    @Nested
    class 재시도_카운트 {

        @Test
        void incrementRetryCount하면_retryCount가_1_증가한다() {
            OutboxEvent event = createEvent();

            event.incrementRetryCount();

            assertThat(event.getRetryCount()).isEqualTo(1);
        }

        @Test
        void 여러번_호출하면_호출_횟수만큼_증가한다() {
            OutboxEvent event = createEvent();

            event.incrementRetryCount();
            event.incrementRetryCount();
            event.incrementRetryCount();

            assertThat(event.getRetryCount()).isEqualTo(3);
        }
    }
}

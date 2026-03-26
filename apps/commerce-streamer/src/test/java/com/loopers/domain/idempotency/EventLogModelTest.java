package com.loopers.domain.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventLogModel 도메인 모델 테스트")
class EventLogModelTest {

    @Nested
    @DisplayName("팩토리 메서드")
    class FactoryMethodTests {

        @Test
        @DisplayName("success 팩토리는 SUCCESS 상태를 설정한다")
        void success_ShouldSetSuccessStatus() {
            EventLogModel log = EventLogModel.success(1L, "ORDER_CREATED", "order-events");

            assertThat(log.getEventId()).isEqualTo(1L);
            assertThat(log.getEventType()).isEqualTo("ORDER_CREATED");
            assertThat(log.getTopic()).isEqualTo("order-events");
            assertThat(log.getStatus()).isEqualTo(EventLogStatus.SUCCESS);
            assertThat(log.getErrorMessage()).isNull();
            assertThat(log.getHandledAt()).isNotNull();
        }

        @Test
        @DisplayName("skipped 팩토리는 SKIPPED 상태를 설정한다")
        void skipped_ShouldSetSkippedStatus() {
            EventLogModel log = EventLogModel.skipped(1L, "ORDER_CREATED", "order-events");

            assertThat(log.getStatus()).isEqualTo(EventLogStatus.SKIPPED);
            assertThat(log.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("failed 팩토리는 FAILED 상태와 에러 메시지를 설정한다")
        void failed_ShouldSetFailedStatusAndError() {
            EventLogModel log = EventLogModel.failed(1L, "ORDER_CREATED", "order-events", "timeout");

            assertThat(log.getStatus()).isEqualTo(EventLogStatus.FAILED);
            assertThat(log.getErrorMessage()).isEqualTo("timeout");
        }
    }
}

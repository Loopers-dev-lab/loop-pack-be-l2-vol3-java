package com.loopers.domain.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboxEventModel 도메인 모델 테스트")
class OutboxEventModelTest {

    @Nested
    @DisplayName("생성 검증")
    class CreateTests {

        @Test
        @DisplayName("유효한 입력으로 생성 시 PENDING 상태이다")
        void create_WithValidInputs_ShouldBePending() {
            OutboxEventModel model = OutboxEventModel.create(
                    "ORDER", "1001", "ORDER_CREATED",
                    "order-events", "1001", "{\"orderId\":1001}"
            );

            assertThat(model.getAggregateType()).isEqualTo("ORDER");
            assertThat(model.getAggregateId()).isEqualTo("1001");
            assertThat(model.getEventType()).isEqualTo("ORDER_CREATED");
            assertThat(model.getTopic()).isEqualTo("order-events");
            assertThat(model.getPartitionKey()).isEqualTo("1001");
            assertThat(model.getPayload()).isEqualTo("{\"orderId\":1001}");
            assertThat(model.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(model.getRetryCount()).isZero();
            assertThat(model.getCreatedAt()).isNotNull();
            assertThat(model.getPublishedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransitionTests {

        @Test
        @DisplayName("markAsPublished 호출 시 PUBLISHED 상태로 전이된다")
        void markAsPublished_ShouldTransitionToPublished() {
            OutboxEventModel model = createTestModel();

            model.markAsPublished();

            assertThat(model.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
            assertThat(model.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("recordFailure 호출 시 retryCount가 증가하고 FAILED 상태가 된다")
        void recordFailure_ShouldIncrementRetryAndFail() {
            OutboxEventModel model = createTestModel();

            model.recordFailure("Kafka timeout");

            assertThat(model.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
            assertThat(model.getRetryCount()).isEqualTo(1);
            assertThat(model.getLastError()).isEqualTo("Kafka timeout");
            assertThat(model.getNextRetryAt()).isNotNull();
        }

        @Test
        @DisplayName("5회 실패 시 DEAD 상태로 전이된다")
        void recordFailure_After5Times_ShouldTransitionToDead() {
            OutboxEventModel model = createTestModel();

            for (int i = 0; i < 5; i++) {
                model.recordFailure("error " + i);
            }

            assertThat(model.getStatus()).isEqualTo(OutboxEventStatus.DEAD);
            assertThat(model.getRetryCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("4회까지는 FAILED 상태를 유지한다")
        void recordFailure_Under5Times_ShouldRemainFailed() {
            OutboxEventModel model = createTestModel();

            for (int i = 0; i < 4; i++) {
                model.recordFailure("error " + i);
            }

            assertThat(model.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
            assertThat(model.getRetryCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("nextRetryAt은 exponential backoff로 증가한다")
        void recordFailure_NextRetryAt_ShouldIncreaseExponentially() {
            OutboxEventModel model = createTestModel();

            model.recordFailure("error 1");
            var firstRetry = model.getNextRetryAt();

            model.recordFailure("error 2");
            var secondRetry = model.getNextRetryAt();

            // 2차 재시도는 1차보다 나중이어야 한다 (exponential backoff)
            assertThat(secondRetry).isAfter(firstRetry);
        }
    }

    private OutboxEventModel createTestModel() {
        return OutboxEventModel.create(
                "ORDER", "1001", "ORDER_CREATED",
                "order-events", "1001", "{}"
        );
    }
}

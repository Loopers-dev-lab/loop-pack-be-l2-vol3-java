package com.loopers.domain.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboxEventModel backoff 테스트")
class OutboxEventBackoffTest {

    @Test
    @DisplayName("recordFailureWithBackoff -- retryCount별 지수 증가 확인")
    void recordFailureWithBackoff_ShouldExponentialDelay() {
        OutboxEventModel event = OutboxEventModel.create(
            "ORDER", "1", "ORDER_CREATED", "order-events", "1", "{}");

        LocalDateTime before = LocalDateTime.now();

        event.recordFailureWithBackoff("error1");  // retry 1 -> 3초
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getNextRetryAt()).isAfter(before.plusSeconds(2));

        event.recordFailureWithBackoff("error2");  // retry 2 -> 9초
        assertThat(event.getRetryCount()).isEqualTo(2);
        assertThat(event.getNextRetryAt()).isAfter(before.plusSeconds(8));

        event.recordFailureWithBackoff("error3");  // retry 3 -> 27초
        assertThat(event.getRetryCount()).isEqualTo(3);
        assertThat(event.getNextRetryAt()).isAfter(before.plusSeconds(26));
    }

    @Test
    @DisplayName("recordFailureWithBackoff -- 5회 실패 시 DEAD 상태로 전이")
    void recordFailureWithBackoff_After5Times_ShouldTransitionToDead() {
        OutboxEventModel event = OutboxEventModel.create(
            "ORDER", "1", "ORDER_CREATED", "order-events", "1", "{}");

        for (int i = 0; i < 5; i++) {
            event.recordFailureWithBackoff("error " + i);
        }

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.DEAD);
        assertThat(event.getRetryCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("recordFailureWithBackoff -- null 에러 메시지는 Unknown error로 대체")
    void recordFailureWithBackoff_NullError_ShouldUseDefault() {
        OutboxEventModel event = OutboxEventModel.create(
            "ORDER", "1", "ORDER_CREATED", "order-events", "1", "{}");

        event.recordFailureWithBackoff(null);

        assertThat(event.getLastError()).isEqualTo("Unknown error");
    }

    @Test
    @DisplayName("recordFailureWithBackoff -- 500자 초과 에러 메시지는 잘림")
    void recordFailureWithBackoff_LongError_ShouldTruncate() {
        OutboxEventModel event = OutboxEventModel.create(
            "ORDER", "1", "ORDER_CREATED", "order-events", "1", "{}");

        String longError = "x".repeat(600);
        event.recordFailureWithBackoff(longError);

        assertThat(event.getLastError()).hasSize(500);
    }
}

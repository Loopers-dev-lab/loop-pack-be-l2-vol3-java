package com.loopers.application.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueuePositionSseConcurrencyLimiterTest {

    @Test
    @DisplayName("max=0이면 tryAcquire가 항상 true이고 release는 무해하다")
    void maxZero_shouldAllowUnbounded() {
        var props = new QueuePositionProperties(175.0, null, new QueuePositionProperties.SseStream(0));
        var limiter = new QueuePositionSseConcurrencyLimiter(props);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        limiter.release();
        limiter.release();
    }

    @Test
    @DisplayName("max=2이면 세 번째 tryAcquire는 false")
    void maxTwo_shouldRejectThirdAcquire() {
        var props = new QueuePositionProperties(175.0, null, new QueuePositionProperties.SseStream(2));
        var limiter = new QueuePositionSseConcurrencyLimiter(props);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();

        limiter.release();
        assertThat(limiter.tryAcquire()).isTrue();
    }
}

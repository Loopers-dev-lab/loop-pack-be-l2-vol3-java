package com.loopers.application.order.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderQueuePollingIntervalPolicyTest {

    private final OrderQueuePollingIntervalPolicy policy = new OrderQueuePollingIntervalPolicy();

    @Test
    @DisplayName("가까운 순번은 최소 polling 간격을 반환한다")
    void resolveReturnsMinimumIntervalForNearRank() {
        long interval = policy.resolve(5L, 5L, properties());

        assertThat(interval).isEqualTo(1L);
    }

    @Test
    @DisplayName("중간 순번은 중간 polling 간격을 반환한다")
    void resolveReturnsMediumInterval() {
        long interval = policy.resolve(200L, 120L, properties());

        assertThat(interval).isEqualTo(3L);
    }

    @Test
    @DisplayName("먼 순번은 최대 polling 간격을 반환한다")
    void resolveReturnsMaximumInterval() {
        long interval = policy.resolve(2000L, 1000L, properties());

        assertThat(interval).isEqualTo(5L);
    }

    private OrderQueueProperties properties() {
        return new OrderQueueProperties(false, 1L, 1, 1L, true, 1000L, 10, 100, true, 5000L, 30000L, "key-ttl", 1L, 5L);
    }
}

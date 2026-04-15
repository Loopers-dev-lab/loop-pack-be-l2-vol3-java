package com.loopers.infrastructure.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumerLagMathTest {

    @Test
    void partitionLag_whenCommittedBehindEnd_returnsDifference() {
        assertThat(ConsumerLagMath.partitionLag(100L, 95L)).isEqualTo(5L);
    }

    @Test
    void partitionLag_whenCommittedNegative_treatsAsZeroCommitted() {
        assertThat(ConsumerLagMath.partitionLag(10L, -1L)).isEqualTo(10L);
    }

    @Test
    void partitionLag_whenCaughtUp_returnsZero() {
        assertThat(ConsumerLagMath.partitionLag(50L, 50L)).isEqualTo(0L);
    }
}

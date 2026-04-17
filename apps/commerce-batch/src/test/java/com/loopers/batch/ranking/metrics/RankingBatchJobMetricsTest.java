package com.loopers.batch.ranking.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankingBatchJobMetricsTest {

    @Test
    @DisplayName("recordSuccess 시 last.success·stale 게이지 등록")
    void recordSuccess_shouldRegisterGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RankingBatchJobMetrics metrics = new RankingBatchJobMetrics(registry);

        metrics.recordSuccess("WEEKLY", "2026W15");

        assertThat(registry.find("batch.rank.job.last.success.epoch").gauges()).isNotEmpty();
        assertThat(registry.find("batch.rank.snapshot.stale.seconds").gauges()).isNotEmpty();
        assertThat(registry.find("batch.rank.job.last.success.epoch").gauge().value()).isGreaterThan(0);
    }
}

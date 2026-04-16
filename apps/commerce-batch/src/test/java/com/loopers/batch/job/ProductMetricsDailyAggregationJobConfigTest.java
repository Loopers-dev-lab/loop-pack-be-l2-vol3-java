package com.loopers.batch.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMetricsDailyAggregationJobConfigTest {

    private ProductMetricsDailyAggregationJobConfig productMetricsDailyAggregationJobConfig;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2025-09-09T15:00:00Z"), ZoneId.of("Asia/Seoul"));
        productMetricsDailyAggregationJobConfig = new ProductMetricsDailyAggregationJobConfig(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                clock
        );
    }

    @Test
    void 오늘_날짜에서_전날을_집계_기준일로_계산한다() {
        LocalDate referenceDate = productMetricsDailyAggregationJobConfig.productMetricsReferenceDate(null);

        assertThat(referenceDate).isEqualTo(LocalDate.of(2025, 9, 9));
    }

    @Test
    void 주간_집계는_전날_기준으로_7일_범위를_계산한다() {
        LocalDate referenceDate = productMetricsDailyAggregationJobConfig.productMetricsReferenceDate(null);

        ProductMetricsDailyAggregationJobConfig.PeriodRange weeklyRange =
                productMetricsDailyAggregationJobConfig.productMetricsWeeklyPeriodRange(referenceDate);

        assertThat(weeklyRange.startDate()).isEqualTo(LocalDate.of(2025, 9, 3));
        assertThat(weeklyRange.endDate()).isEqualTo(LocalDate.of(2025, 9, 9));
    }

    @Test
    void 월간_집계는_전날_기준으로_30일_범위를_계산한다() {
        LocalDate referenceDate = productMetricsDailyAggregationJobConfig.productMetricsReferenceDate(null);

        ProductMetricsDailyAggregationJobConfig.PeriodRange monthlyRange =
                productMetricsDailyAggregationJobConfig.productMetricsMonthlyPeriodRange(referenceDate);

        assertThat(monthlyRange.startDate()).isEqualTo(LocalDate.of(2025, 8, 11));
        assertThat(monthlyRange.endDate()).isEqualTo(LocalDate.of(2025, 9, 9));
    }
}

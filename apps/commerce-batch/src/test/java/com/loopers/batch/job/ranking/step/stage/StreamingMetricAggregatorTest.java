package com.loopers.batch.job.ranking.step.stage;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StreamingMetricAggregatorTest {

    private static final LocalDateTime LAST_7D_START  = LocalDateTime.of(2026, 4, 8, 0, 0);
    private static final LocalDateTime LAST_30D_START = LocalDateTime.of(2026, 3, 16, 0, 0);

    @Nested
    class 집계 {

        @Test
        void 같은_product_의_연속된_row_는_한_AggregatedMetric_으로_합쳐진다() throws Exception {
            ListSource source = new ListSource(List.of(
                    row(1L, LAST_7D_START,                    5),  // 7d 포함
                    row(1L, LAST_7D_START.plusDays(3),       10),  // 7d 포함
                    row(2L, LAST_30D_START,                   3)   // 30d 만
            ));
            StreamingMetricAggregator agg = new StreamingMetricAggregator(source, LAST_7D_START);

            AggregatedMetric first  = agg.next();
            AggregatedMetric second = agg.next();
            AggregatedMetric end    = agg.next();

            assertAll(
                    () -> assertThat(first.productId()).isEqualTo(1L),
                    () -> assertThat(first.sum7d()).isEqualTo(15),
                    () -> assertThat(first.sum30d()).isEqualTo(15),
                    () -> assertThat(second.productId()).isEqualTo(2L),
                    () -> assertThat(second.sum7d()).isEqualTo(0),
                    () -> assertThat(second.sum30d()).isEqualTo(3),
                    () -> assertThat(end).isNull()
            );
        }

        @Test
        void bucket_time_이_last7dStart_보다_앞이면_sum7d_에는_포함되지_않고_sum30d_에만_포함된다() throws Exception {
            ListSource source = new ListSource(List.of(
                    row(1L, LAST_30D_START,                       7),    // 30d O, 7d X
                    row(1L, LAST_7D_START.minusSeconds(1),        2),    // 30d O, 7d X (경계 직전)
                    row(1L, LAST_7D_START,                        3)     // 30d O, 7d O (경계 포함)
            ));
            StreamingMetricAggregator agg = new StreamingMetricAggregator(source, LAST_7D_START);

            AggregatedMetric result = agg.next();

            assertAll(
                    () -> assertThat(result.sum30d()).isEqualTo(12),
                    () -> assertThat(result.sum7d()).isEqualTo(3)
            );
        }
    }

    @Nested
    class 빈_소스 {

        @Test
        void 소스가_비어_있으면_첫_호출부터_null_을_반환한다() throws Exception {
            StreamingMetricAggregator agg = new StreamingMetricAggregator(new ListSource(List.of()), LAST_7D_START);

            assertThat(agg.next()).isNull();
        }

        @Test
        void 한_번_exhausted_되면_이후_호출도_항상_null_을_반환한다() throws Exception {
            ListSource source = new ListSource(List.of(row(1L, LAST_7D_START, 5)));
            StreamingMetricAggregator agg = new StreamingMetricAggregator(source, LAST_7D_START);

            assertAll(
                    () -> assertThat(agg.next()).isNotNull(),
                    () -> assertThat(agg.next()).isNull(),
                    () -> assertThat(agg.next()).isNull()
            );
        }
    }

    @Nested
    class 대량_처리 {

        @Test
        void 한_상품이_많은_row_를_가져도_O1_메모리로_처리된다() throws Exception {
            int chainLength = 8_640;
            ListSource source = new ListSource(generateChain(1L, chainLength));
            StreamingMetricAggregator agg = new StreamingMetricAggregator(source, LAST_7D_START);

            AggregatedMetric result = agg.next();

            assertAll(
                    () -> assertThat(result.productId()).isEqualTo(1L),
                    () -> assertThat(result.sum30d()).isEqualTo(chainLength),
                    () -> assertThat(agg.next()).isNull()
            );
        }
    }

    private static RawMetricRow row(long productId, LocalDateTime bucketTime, long count) {
        return new RawMetricRow(productId, bucketTime, count);
    }

    private static List<RawMetricRow> generateChain(long productId, int size) {
        List<RawMetricRow> rows = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(row(productId, LAST_30D_START.plusMinutes(5L * i), 1));
        }
        return rows;
    }

    /** 테스트용 RowSource — List 를 큐로 소비한다. */
    private static final class ListSource implements StreamingMetricAggregator.RowSource {
        private final Deque<RawMetricRow> queue;

        ListSource(List<RawMetricRow> rows) {
            this.queue = new ArrayDeque<>(rows);
        }

        @Override
        public RawMetricRow readOne() {
            return queue.pollFirst();
        }
    }
}

package com.loopers.batch.job.cleanup.step;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.IsoFields;

import static org.assertj.core.api.Assertions.assertThat;

class StagingCleanupTaskletTest {

    @Nested
    @DisplayName("computeWeeklyThreshold 는 현재 주 포함 최근 4주 보존 경계를 반환한다.")
    class WeeklyThreshold {

        @Test
        void returnsThresholdYearWeek_forToday() {
            // today 가 N주차면 threshold = N-3 주차 (현재 포함 4주 보존 → 가장 오래된 보존 주차)
            LocalDate today = LocalDate.of(2026, 4, 15); // 2026-W16 (수요일)
            String threshold = StagingCleanupTasklet.computeWeeklyThreshold(today);

            // 2026-W13 이어야 함 (16 - 3)
            assertThat(threshold).isEqualTo("2026-W13");
        }

        @Test
        void handlesIsoWeekYearBoundary() {
            // 2026-01-05(월) 은 2026-W02 의 월요일. W02 - 3 = W51 (2025년, 2025-12-22 주)
            LocalDate today = LocalDate.of(2026, 1, 5);
            String threshold = StagingCleanupTasklet.computeWeeklyThreshold(today);

            assertThat(threshold).isEqualTo("2025-W51");
        }

        @Test
        void zeroPaddingIsEnforced() {
            // 월 초 몇 주차 시점에도 포맷이 "YYYY-W0N" (2자리) 으로 나와야 문자열 비교 안전성 확보
            LocalDate today = LocalDate.of(2026, 2, 1); // 2026-W05
            String threshold = StagingCleanupTasklet.computeWeeklyThreshold(today);

            assertThat(threshold).matches("\\d{4}-W\\d{2}");
            // 2026-W05 - 3 = 2026-W02
            assertThat(threshold).isEqualTo("2026-W02");
        }

        @Test
        @DisplayName("year_week 문자열 비교가 연말/연초 경계에서도 안전하다 (2025-W52 < 2026-W01).")
        void stringComparisonRespectsYearBoundary() {
            // "2025-W52" < "2026-W01" 는 문자열 사전순으로도 true (연도 prefix 4자리)
            // 즉 cleanup 의 year_week < :threshold SQL 이 연말에도 의도대로 동작한다.
            String w52 = "2025-W52";
            String w01 = "2026-W01";

            assertThat(w52.compareTo(w01)).isLessThan(0);
            assertThat(w01.compareTo(w52)).isGreaterThan(0);

            // Threshold 가 2026-W02 면 2025-W52 는 삭제 대상 (< 2026-W02)
            String threshold = "2026-W02";
            assertThat(w52.compareTo(threshold)).isLessThan(0);
            assertThat(w01.compareTo(threshold)).isLessThan(0);
            assertThat(threshold.compareTo(threshold)).isZero();
        }

        @Test
        void boundaryMeaning_isOldestRetainedWeek() {
            // "오늘 포함 최근 4주 보존" 계약:
            //   threshold = oldestRetainedYearWeek, SQL 은 year_week < threshold 이므로
            //   threshold 자체는 보존되고 그보다 오래된 주차만 삭제된다.
            LocalDate today = LocalDate.of(2026, 4, 8); // 2026-W15
            String threshold = StagingCleanupTasklet.computeWeeklyThreshold(today);

            int todayYear = today.get(IsoFields.WEEK_BASED_YEAR);
            int todayWeek = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            String expected = String.format("%04d-W%02d", todayYear, todayWeek - 3);

            assertThat(threshold).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("computeMonthlyThreshold 는 현재 월 포함 최근 3개월 보존 경계를 반환한다.")
    class MonthlyThreshold {

        @Test
        void returnsThresholdYearMonth_forToday() {
            LocalDate today = LocalDate.of(2026, 4, 10);
            String threshold = StagingCleanupTasklet.computeMonthlyThreshold(today);

            // 4월 - 2 = 2월 (현재 포함 3개월 보존 → 가장 오래된 보존 월)
            assertThat(threshold).isEqualTo("2026-02");
        }

        @Test
        void handlesYearBoundary() {
            LocalDate today = LocalDate.of(2026, 2, 15);
            String threshold = StagingCleanupTasklet.computeMonthlyThreshold(today);

            // 2026-02 - 2 = 2025-12
            assertThat(threshold).isEqualTo("2025-12");
        }

        @Test
        void zeroPaddingIsEnforced() {
            LocalDate today = LocalDate.of(2026, 11, 5);
            String threshold = StagingCleanupTasklet.computeMonthlyThreshold(today);

            assertThat(threshold).matches("\\d{4}-\\d{2}");
            assertThat(threshold).isEqualTo("2026-09");
        }
    }
}

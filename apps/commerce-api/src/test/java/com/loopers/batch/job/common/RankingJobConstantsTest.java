package com.loopers.batch.job.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 랭킹 Job 공통 상수 및 유틸리티 테스트.
 */
@DisplayName("RankingJobConstants 테스트")
class RankingJobConstantsTest {

    @Nested
    @DisplayName("DATE_FORMATTER")
    class DateFormatterTest {

        @Test
        @DisplayName("yyyyMMdd 형식으로 날짜를 파싱한다")
        void parseDateFormat() {
            // Arrange
            String dateStr = "20250414";

            // Act
            LocalDate date = LocalDate.parse(dateStr, RankingJobConstants.DATE_FORMATTER);

            // Assert
            assertThat(date.getYear()).isEqualTo(2025);
            assertThat(date.getMonthValue()).isEqualTo(4);
            assertThat(date.getDayOfMonth()).isEqualTo(14);
        }

        @Test
        @DisplayName("yyyyMMdd 형식으로 날짜를 포맷한다")
        void formatDate() {
            // Arrange
            LocalDate date = LocalDate.of(2025, 4, 14);

            // Act
            String formatted = date.format(RankingJobConstants.DATE_FORMATTER);

            // Assert
            assertThat(formatted).isEqualTo("20250414");
        }
    }

    @Nested
    @DisplayName("buildAggregationSql")
    class BuildAggregationSqlTest {

        @Test
        @DisplayName("시작일과 종료일이 SQL에 포함된다")
        void containsDateRange() {
            // Act
            String sql = RankingJobConstants.buildAggregationSql("2025-04-14", "2025-04-20");

            // Assert
            assertThat(sql).contains("'2025-04-14'");
            assertThat(sql).contains("'2025-04-20'");
        }

        @Test
        @DisplayName("TOP_N 값이 LIMIT에 포함된다")
        void containsLimit() {
            // Act
            String sql = RankingJobConstants.buildAggregationSql("2025-04-01", "2025-04-30");

            // Assert
            assertThat(sql).contains("LIMIT " + RankingJobConstants.TOP_N);
        }

        @Test
        @DisplayName("GROUP BY와 ORDER BY가 포함된다")
        void containsGroupByAndOrderBy() {
            // Act
            String sql = RankingJobConstants.buildAggregationSql("2025-04-01", "2025-04-30");

            // Assert
            assertThat(sql).contains("GROUP BY product_id");
            assertThat(sql).contains("ORDER BY total_score DESC");
        }
    }

    @Nested
    @DisplayName("주간 날짜 범위 계산")
    class WeeklyDateRangeTest {

        @Test
        @DisplayName("월요일이 주의 시작일이다")
        void weekStartIsMonday() {
            // Arrange
            LocalDate wednesday = LocalDate.of(2025, 4, 16); // 수요일

            // Act
            LocalDate weekStart = wednesday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

            // Assert
            assertThat(weekStart).isEqualTo(LocalDate.of(2025, 4, 14)); // 월요일
            assertThat(weekStart.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        }

        @Test
        @DisplayName("일요일이 주의 종료일이다")
        void weekEndIsSunday() {
            // Arrange
            LocalDate wednesday = LocalDate.of(2025, 4, 16); // 수요일

            // Act
            LocalDate weekEnd = wednesday.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

            // Assert
            assertThat(weekEnd).isEqualTo(LocalDate.of(2025, 4, 20)); // 일요일
            assertThat(weekEnd.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        }

        @Test
        @DisplayName("월요일 입력 시 해당 월요일이 시작일이다")
        void mondayInputReturnsItself() {
            // Arrange
            LocalDate monday = LocalDate.of(2025, 4, 14); // 월요일

            // Act
            LocalDate weekStart = monday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

            // Assert
            assertThat(weekStart).isEqualTo(monday);
        }

        @Test
        @DisplayName("일요일 입력 시 해당 일요일이 종료일이다")
        void sundayInputReturnsItself() {
            // Arrange
            LocalDate sunday = LocalDate.of(2025, 4, 20); // 일요일

            // Act
            LocalDate weekEnd = sunday.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

            // Assert
            assertThat(weekEnd).isEqualTo(sunday);
        }
    }

    @Nested
    @DisplayName("월간 날짜 범위 계산")
    class MonthlyDateRangeTest {

        @Test
        @DisplayName("1월의 시작일과 종료일")
        void januaryRange() {
            // Arrange
            LocalDate midJanuary = LocalDate.of(2025, 1, 15);
            YearMonth yearMonth = YearMonth.from(midJanuary);

            // Act
            LocalDate monthStart = yearMonth.atDay(1);
            LocalDate monthEnd = yearMonth.atEndOfMonth();

            // Assert
            assertThat(monthStart).isEqualTo(LocalDate.of(2025, 1, 1));
            assertThat(monthEnd).isEqualTo(LocalDate.of(2025, 1, 31));
        }

        @Test
        @DisplayName("윤년 2월의 종료일은 29일")
        void leapYearFebruary() {
            // Arrange
            LocalDate midFebruary = LocalDate.of(2024, 2, 15); // 2024년은 윤년
            YearMonth yearMonth = YearMonth.from(midFebruary);

            // Act
            LocalDate monthEnd = yearMonth.atEndOfMonth();

            // Assert
            assertThat(monthEnd).isEqualTo(LocalDate.of(2024, 2, 29));
        }

        @Test
        @DisplayName("평년 2월의 종료일은 28일")
        void nonLeapYearFebruary() {
            // Arrange
            LocalDate midFebruary = LocalDate.of(2025, 2, 15); // 2025년은 평년
            YearMonth yearMonth = YearMonth.from(midFebruary);

            // Act
            LocalDate monthEnd = yearMonth.atEndOfMonth();

            // Assert
            assertThat(monthEnd).isEqualTo(LocalDate.of(2025, 2, 28));
        }

        @Test
        @DisplayName("4월의 종료일은 30일")
        void aprilRange() {
            // Arrange
            LocalDate midApril = LocalDate.of(2025, 4, 15);
            YearMonth yearMonth = YearMonth.from(midApril);

            // Act
            LocalDate monthEnd = yearMonth.atEndOfMonth();

            // Assert
            assertThat(monthEnd).isEqualTo(LocalDate.of(2025, 4, 30));
        }
    }

    @Nested
    @DisplayName("상수 값 검증")
    class ConstantsTest {

        @Test
        @DisplayName("CHUNK_SIZE는 100이다")
        void chunkSizeIs100() {
            assertThat(RankingJobConstants.CHUNK_SIZE).isEqualTo(100);
        }

        @Test
        @DisplayName("TOP_N은 100이다")
        void topNIs100() {
            assertThat(RankingJobConstants.TOP_N).isEqualTo(100);
        }
    }
}
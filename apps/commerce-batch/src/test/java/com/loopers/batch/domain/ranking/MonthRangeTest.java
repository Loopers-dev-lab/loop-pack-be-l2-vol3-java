package com.loopers.batch.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonthRangeTest {

    @Nested
    @DisplayName("연월 문자열로 생성할 때, ")
    class Of {

        @Test
        void returnsFirstToLastDayOfMonth() {
            MonthRange range = MonthRange.of("2026-04");

            assertThat(range.yearMonth()).isEqualTo("2026-04");
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 4, 30));
        }

        @Test
        void returnsBucketKeyFormatInBasicIsoDate() {
            MonthRange range = MonthRange.of("2026-04");

            assertThat(range.startKey()).isEqualTo("20260401");
            assertThat(range.endKey()).isEqualTo("20260430");
        }

        @Test
        void handles31DayMonth() {
            MonthRange range = MonthRange.of("2026-01");

            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 1, 31));
        }

        @Test
        void handlesFebruaryLeapYear() {
            MonthRange range = MonthRange.of("2024-02");

            assertThat(range.end()).isEqualTo(LocalDate.of(2024, 2, 29));
        }

        @Test
        void handlesFebruaryNonLeapYear() {
            MonthRange range = MonthRange.of("2026-02");

            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        void throwsException_whenYearMonthIsNull() {
            assertThatThrownBy(() -> MonthRange.of(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throwsException_whenYearMonthIsBlank() {
            assertThatThrownBy(() -> MonthRange.of(" "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throwsException_whenFormatIsInvalid() {
            assertThatThrownBy(() -> MonthRange.of("2026/04"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MonthRange.of("202604"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MonthRange.of("2026-4"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throwsException_whenMonthOutOfRange() {
            assertThatThrownBy(() -> MonthRange.of("2026-00"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> MonthRange.of("2026-13"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("기준 일자의 이전 달을 계산할 때, ")
    class OfPreviousMonth {

        @Test
        void returnsPreviousMonth_fromFirstDay() {
            // 2026-04-01 기준 이전 달은 2026-03
            MonthRange range = MonthRange.ofPreviousMonth(LocalDate.of(2026, 4, 1));

            assertThat(range.yearMonth()).isEqualTo("2026-03");
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 3, 31));
        }

        @Test
        void returnsPreviousMonth_fromMidMonth() {
            MonthRange range = MonthRange.ofPreviousMonth(LocalDate.of(2026, 4, 15));

            assertThat(range.yearMonth()).isEqualTo("2026-03");
        }

        @Test
        void handlesJanuary_returnsDecemberOfPreviousYear() {
            MonthRange range = MonthRange.ofPreviousMonth(LocalDate.of(2026, 1, 1));

            assertThat(range.yearMonth()).isEqualTo("2025-12");
            assertThat(range.start()).isEqualTo(LocalDate.of(2025, 12, 1));
            assertThat(range.end()).isEqualTo(LocalDate.of(2025, 12, 31));
        }
    }
}

package com.loopers.batch.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeekRangeTest {

    @Nested
    @DisplayName("ISO 주차 문자열로 생성할 때, ")
    class Of {

        @Test
        void returnsMondayToSunday_forIsoWeekString() {
            WeekRange range = WeekRange.of("2026-W15");

            assertThat(range.yearWeek()).isEqualTo("2026-W15");
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 4, 6));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 4, 12));
        }

        @Test
        void returnsBucketKeyFormatInBasicIsoDate() {
            WeekRange range = WeekRange.of("2026-W15");

            assertThat(range.startKey()).isEqualTo("20260406");
            assertThat(range.endKey()).isEqualTo("20260412");
        }

        @Test
        void handlesWeekThatStraddlesMonthBoundary() {
            WeekRange range = WeekRange.of("2026-W14");

            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 3, 30));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 4, 5));
        }

        @Test
        void handlesWeekThatStraddlesYearBoundary() {
            // 2026-W01 은 ISO 기준으로 2025-12-29(월) ~ 2026-01-04(일)
            WeekRange range = WeekRange.of("2026-W01");

            assertThat(range.start()).isEqualTo(LocalDate.of(2025, 12, 29));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 1, 4));
        }

        @Test
        void handlesLastWeekOfYear() {
            // 2025-W52 는 2025-12-22(월) ~ 2025-12-28(일)
            WeekRange range = WeekRange.of("2025-W52");

            assertThat(range.start()).isEqualTo(LocalDate.of(2025, 12, 22));
            assertThat(range.end()).isEqualTo(LocalDate.of(2025, 12, 28));
        }

        @Test
        void throwsException_whenYearWeekIsNull() {
            assertThatThrownBy(() -> WeekRange.of(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throwsException_whenYearWeekIsBlank() {
            assertThatThrownBy(() -> WeekRange.of("  "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throwsException_whenFormatIsInvalid() {
            assertThatThrownBy(() -> WeekRange.of("2026-15"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> WeekRange.of("W15"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> WeekRange.of("2026W15"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throwsException_whenWeekNumberOutOfRange() {
            assertThatThrownBy(() -> WeekRange.of("2026-W00"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> WeekRange.of("2026-W54"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("기준 일자의 이전 주를 계산할 때, ")
    class OfPreviousWeek {

        @Test
        void returnsPreviousIsoWeek_fromMonday() {
            // 2026-04-13(월) 기준 이전 주는 2026-W15
            WeekRange range = WeekRange.ofPreviousWeek(LocalDate.of(2026, 4, 13));

            assertThat(range.yearWeek()).isEqualTo("2026-W15");
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 4, 6));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 4, 12));
        }

        @Test
        void returnsPreviousIsoWeek_fromMidWeek() {
            // 2026-04-08(수) 기준 이전 주는 2026-W14
            WeekRange range = WeekRange.ofPreviousWeek(LocalDate.of(2026, 4, 8));

            assertThat(range.yearWeek()).isEqualTo("2026-W14");
        }

        @Test
        void handlesYearBoundary_whenMondayOfFirstWeek() {
            // 2026-01-05(월) 기준 이전 주는 2026-W01 (2025-12-29 ~ 2026-01-04)
            WeekRange range = WeekRange.ofPreviousWeek(LocalDate.of(2026, 1, 5));

            assertThat(range.yearWeek()).isEqualTo("2026-W01");
        }
    }
}

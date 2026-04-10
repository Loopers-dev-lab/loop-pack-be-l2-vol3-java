package com.loopers.application.ranking;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RankingDateUtilsTest {

    @Nested
    class KST_날짜를_UTC_경계로_변환 {

        @Test
        void KST_자정은_UTC_전날_15시가_된다() {
            LocalDateTime result = RankingDateUtils.kstDateToUtcBoundary(LocalDate.of(2026, 4, 10));

            assertThat(result).isEqualTo(LocalDateTime.of(2026, 4, 9, 15, 0));
        }

        @Test
        void KST_1월1일은_UTC_전년도_12월31일_15시가_된다() {
            LocalDateTime result = RankingDateUtils.kstDateToUtcBoundary(LocalDate.of(2026, 1, 1));

            assertThat(result).isEqualTo(LocalDateTime.of(2025, 12, 31, 15, 0));
        }

        @Test
        void 연속된_날짜의_차이는_정확히_24시간이다() {
            LocalDateTime day1 = RankingDateUtils.kstDateToUtcBoundary(LocalDate.of(2026, 4, 10));
            LocalDateTime day2 = RankingDateUtils.kstDateToUtcBoundary(LocalDate.of(2026, 4, 11));

            assertThat(java.time.Duration.between(day1, day2)).isEqualTo(java.time.Duration.ofHours(24));
        }
    }
}

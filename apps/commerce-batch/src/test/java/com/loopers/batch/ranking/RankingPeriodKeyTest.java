package com.loopers.batch.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingPeriodKeyTest {

    @Test
    @DisplayName("weekly: 같은 ISO 주에 속한 날짜들은 동일한 yyyyMMdd(월요일 앵커) 키를 가진다.")
    void weekly_whenDatesInSameIsoWeek_shouldShareMondayAnchor() {
        // given
        LocalDate wednesday = LocalDate.of(2026, 4, 8); // 수요일
        LocalDate friday = LocalDate.of(2026, 4, 10);   // 같은 주 금요일

        // when
        String key1 = RankingPeriodKey.weekly(wednesday);
        String key2 = RankingPeriodKey.weekly(friday);

        // then
        assertThat(key1).isEqualTo("20260406"); // 해당 주 월요일
        assertThat(key2).isEqualTo("20260406");
    }

    @Test
    @DisplayName("weekly: 2026-01-01(목) 연초는 ISO 주의 월요일 앵커(전년 12/29)로 귀속된다.")
    void weekly_whenNewYearsDay2026_shouldMapToMondayAnchorInPriorDecember() {
        LocalDate newYears = LocalDate.of(2026, 1, 1);
        LocalDate mondaySameIsoWeek = LocalDate.of(2025, 12, 29);

        assertThat(RankingPeriodKey.weekly(newYears)).isEqualTo("20251229");
        assertThat(RankingPeriodKey.weekly(mondaySameIsoWeek)).isEqualTo("20251229");
    }

    @Test
    @DisplayName("weekly: 월요일 시작 규칙 — 앵커는 항상 해당 주의 월요일 yyyyMMdd이다.")
    void weekly_whenSundayInWeek_shouldStillResolveToMondayOfThatWeek() {
        LocalDate sunday = LocalDate.of(2026, 4, 12);
        assertThat(RankingPeriodKey.weekly(sunday)).isEqualTo("20260406");
    }

    @Test
    @DisplayName("monthly: yyyyMMdd 형식으로 해당 월의 첫째 날 키를 만든다.")
    void monthly_whenLocalDateGiven_shouldReturnFirstDayYyyyMmDd() {
        // given
        LocalDate date = LocalDate.of(2026, 4, 16);

        // when
        String key = RankingPeriodKey.monthly(date);

        // then
        assertThat(key).isEqualTo("20260401");
    }

    @Test
    @DisplayName("null을 넘기면 IllegalArgumentException을 던진다.")
    void weeklyAndMonthly_whenNull_shouldThrow() {
        assertThatThrownBy(() -> RankingPeriodKey.weekly(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RankingPeriodKey.monthly(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}


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


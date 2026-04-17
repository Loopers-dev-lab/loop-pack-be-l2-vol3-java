package com.loopers.domain.ranking;

import com.loopers.batch.domain.ranking.RankingPeriodKeyFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingPeriodKeyFactoryTest {

    @DisplayName("ISO week 경계를 기준으로 weekly key를 계산한다")
    @Test
    void weeklyKey() {
        assertThat(RankingPeriodKeyFactory.toWeeklyKey(LocalDate.of(2025, 12, 29))).isEqualTo("2026-W01");
        assertThat(RankingPeriodKeyFactory.toWeeklyKey(LocalDate.of(2026, 4, 15))).isEqualTo("2026-W16");
    }

    @DisplayName("monthly key를 계산한다")
    @Test
    void monthlyKey() {
        assertThat(RankingPeriodKeyFactory.toMonthlyKey(LocalDate.of(2026, 4, 15))).isEqualTo("2026-04");
    }
}

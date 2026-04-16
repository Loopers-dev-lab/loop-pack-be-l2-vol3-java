package com.loopers.interfaces.api.ranking;

import com.loopers.domain.ranking.RankingPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RankingPeriod 파싱 테스트")
class RankingV1ControllerPeriodTest {

    @DisplayName("null → DAILY")
    @Test
    void nullInput_returnDaily() {
        assertThat(RankingPeriod.fromString(null)).isEqualTo(RankingPeriod.DAILY);
    }

    @DisplayName("빈 문자열 → DAILY")
    @Test
    void emptyInput_returnDaily() {
        assertThat(RankingPeriod.fromString("")).isEqualTo(RankingPeriod.DAILY);
    }

    @DisplayName("daily → DAILY")
    @Test
    void daily() {
        assertThat(RankingPeriod.fromString("daily")).isEqualTo(RankingPeriod.DAILY);
    }

    @DisplayName("weekly → WEEKLY (대소문자 무관)")
    @Test
    void weekly_caseInsensitive() {
        assertThat(RankingPeriod.fromString("weekly")).isEqualTo(RankingPeriod.WEEKLY);
        assertThat(RankingPeriod.fromString("WEEKLY")).isEqualTo(RankingPeriod.WEEKLY);
        assertThat(RankingPeriod.fromString("Weekly")).isEqualTo(RankingPeriod.WEEKLY);
    }

    @DisplayName("monthly → MONTHLY")
    @Test
    void monthly() {
        assertThat(RankingPeriod.fromString("monthly")).isEqualTo(RankingPeriod.MONTHLY);
    }

    @DisplayName("quarterly → QUARTERLY")
    @Test
    void quarterly() {
        assertThat(RankingPeriod.fromString("quarterly")).isEqualTo(RankingPeriod.QUARTERLY);
        assertThat(RankingPeriod.fromString("QUARTERLY")).isEqualTo(RankingPeriod.QUARTERLY);
    }

    @DisplayName("invalid → IllegalArgumentException")
    @Test
    void invalid_throwsException() {
        assertThatThrownBy(() -> RankingPeriod.fromString("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ranking period");
    }
}

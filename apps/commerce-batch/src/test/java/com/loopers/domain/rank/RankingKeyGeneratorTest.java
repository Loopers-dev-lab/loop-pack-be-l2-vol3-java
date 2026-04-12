package com.loopers.domain.rank;

import com.loopers.ranking.RankingKeyGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingKeyGeneratorTest {

    @DisplayName("월요일의 weeklyPeriodKey를 생성한다")
    @Test
    void weeklyPeriodKey_monday() {
        LocalDate monday = LocalDate.of(2026, 4, 13);
        assertThat(RankingKeyGenerator.weeklyPeriodKey(monday)).isEqualTo("2026W16");
    }

    @DisplayName("일요일은 로케일 기반 주 번호로 다음 주에 속한다")
    @Test
    void weeklyPeriodKey_sunday() {
        LocalDate sunday = LocalDate.of(2026, 4, 12);
        assertThat(RankingKeyGenerator.weeklyPeriodKey(sunday)).isEqualTo("2026W16");
    }

    @DisplayName("연말 경계: 12/29(월)는 ISO week 기준 다음 해 첫째 주에 속할 수 있다")
    @Test
    void weeklyPeriodKey_yearBoundary() {
        LocalDate dec29 = LocalDate.of(2025, 12, 29);
        String key = RankingKeyGenerator.weeklyPeriodKey(dec29);
        assertThat(key).isEqualTo("2026W01");
    }

    @DisplayName("monthlyPeriodKey를 생성한다")
    @Test
    void monthlyPeriodKey() {
        assertThat(RankingKeyGenerator.monthlyPeriodKey(LocalDate.of(2026, 4, 1))).isEqualTo("202604");
        assertThat(RankingKeyGenerator.monthlyPeriodKey(LocalDate.of(2026, 12, 31))).isEqualTo("202612");
    }

    @DisplayName("weekStart는 해당 주 월요일을 반환한다")
    @Test
    void weekStart() {
        LocalDate wednesday = LocalDate.of(2026, 4, 15);
        assertThat(RankingKeyGenerator.weekStart(wednesday)).isEqualTo(LocalDate.of(2026, 4, 13));
    }

    @DisplayName("weekEnd는 해당 주 일요일을 반환한다")
    @Test
    void weekEnd() {
        LocalDate wednesday = LocalDate.of(2026, 4, 15);
        assertThat(RankingKeyGenerator.weekEnd(wednesday)).isEqualTo(LocalDate.of(2026, 4, 19));
    }

    @DisplayName("monthStart는 1일을 반환한다")
    @Test
    void monthStart() {
        assertThat(RankingKeyGenerator.monthStart(LocalDate.of(2026, 4, 15))).isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @DisplayName("monthEnd는 말일을 반환한다")
    @Test
    void monthEnd() {
        assertThat(RankingKeyGenerator.monthEnd(LocalDate.of(2026, 2, 15))).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @DisplayName("윤년 2월 monthEnd는 29일이다")
    @Test
    void monthEnd_leapYear() {
        assertThat(RankingKeyGenerator.monthEnd(LocalDate.of(2028, 2, 15))).isEqualTo(LocalDate.of(2028, 2, 29));
    }
}

package com.loopers.domain.rank;

import com.loopers.ranking.RankingKeyGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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

    @DisplayName("quarterlyPeriodKey는 종료일 yyyyMMdd 형식이다")
    @Test
    void quarterlyPeriodKey() {
        assertThat(RankingKeyGenerator.quarterlyPeriodKey(LocalDate.of(2026, 4, 16))).isEqualTo("20260416");
        assertThat(RankingKeyGenerator.quarterlyPeriodKey(LocalDate.of(2026, 1, 1))).isEqualTo("20260101");
        assertThat(RankingKeyGenerator.quarterlyPeriodKey(LocalDate.of(2025, 12, 31))).isEqualTo("20251231");
    }

    @DisplayName("quarterlyStart는 종료일 포함 90일 윈도우의 시작일(종료일 -89일)이다")
    @Test
    void quarterlyStart() {
        LocalDate endDate = LocalDate.of(2026, 4, 16);
        LocalDate start = RankingKeyGenerator.quarterlyStart(endDate);
        assertThat(start).isEqualTo(LocalDate.of(2026, 1, 17));
        assertThat(ChronoUnit.DAYS.between(start, endDate)).isEqualTo(89);
    }

    @DisplayName("quarterlyEnd는 입력 date를 그대로 반환한다")
    @Test
    void quarterlyEnd() {
        LocalDate date = LocalDate.of(2026, 4, 16);
        assertThat(RankingKeyGenerator.quarterlyEnd(date)).isEqualTo(date);
    }

    @DisplayName("quarterly 시작·종료일 사이 일수는 90일을 포함한다")
    @Test
    void quarterlyWindow_isExactly90Days() {
        LocalDate endDate = LocalDate.of(2026, 4, 16);
        LocalDate start = RankingKeyGenerator.quarterlyStart(endDate);
        LocalDate end = RankingKeyGenerator.quarterlyEnd(endDate);
        assertThat(ChronoUnit.DAYS.between(start, end) + 1).isEqualTo(90);
    }

    @DisplayName("윤년 경계를 가로지르는 quarterly 윈도우도 정확히 90일이다")
    @Test
    void quarterlyWindow_acrossLeapYear() {
        LocalDate endDate = LocalDate.of(2028, 3, 31);
        LocalDate start = RankingKeyGenerator.quarterlyStart(endDate);
        assertThat(start).isEqualTo(LocalDate.of(2028, 1, 2));
        assertThat(ChronoUnit.DAYS.between(start, endDate) + 1).isEqualTo(90);
    }

    @DisplayName("previousQuarterlyPeriodKey는 어제 날짜의 quarterly key이다")
    @Test
    void previousQuarterlyPeriodKey() {
        assertThat(RankingKeyGenerator.previousQuarterlyPeriodKey(LocalDate.of(2026, 4, 16))).isEqualTo("20260415");
        assertThat(RankingKeyGenerator.previousQuarterlyPeriodKey(LocalDate.of(2026, 1, 1))).isEqualTo("20251231");
    }
}

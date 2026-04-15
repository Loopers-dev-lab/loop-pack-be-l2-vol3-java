package com.loopers.batch.job.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IsoPeriodMath 단위 테스트 — ISO 8601 주 경계의 edge case 지뢰밭.
 *
 * 연초/연말 경계를 특히 강하게 검증한다.
 * ISO 주 규칙: 1월 4일을 포함하는 주 = W01.
 */
class IsoPeriodMathTest {

    @DisplayName("ISO year-week 계산 — 평일/연초/연말 경계")
    @ParameterizedTest
    @CsvSource({
            // 평범한 수요일
            "2026-04-15, 2026-W16",
            // 주의 시작 월요일
            "2026-04-13, 2026-W16",
            // 주의 마지막 일요일
            "2026-04-19, 2026-W16",
            // ISO 연초 경계: 2025-12-29(월)은 2026-W01
            "2025-12-29, 2026-W01",
            "2025-12-30, 2026-W01",
            "2025-12-31, 2026-W01",
            // ISO 연말 경계: 2027-01-01(금)은 2026-W53
            "2027-01-01, 2026-W53",
            "2027-01-03, 2026-W53",
            // 2025 W01 시작
            "2024-12-30, 2025-W01"
    })
    void yearWeekOf(LocalDate input, String expected) {
        assertThat(IsoPeriodMath.yearWeekOf(input)).isEqualTo(expected);
    }

    @DisplayName("주의 시작(월)/끝(일) 계산")
    @ParameterizedTest
    @CsvSource({
            // 수요일 기준
            "2026-04-15, 2026-04-13, 2026-04-19",
            // 연초 경계 주
            "2025-12-30, 2025-12-29, 2026-01-04",
            // 연말 경계 주
            "2027-01-01, 2026-12-28, 2027-01-03"
    })
    void isoWeekBoundaries(LocalDate date, LocalDate expectedStart, LocalDate expectedEnd) {
        assertThat(IsoPeriodMath.startOfIsoWeek(date)).isEqualTo(expectedStart);
        assertThat(IsoPeriodMath.endOfIsoWeek(date)).isEqualTo(expectedEnd);
    }

    @DisplayName("월간 키 + 시작/끝")
    @ParameterizedTest
    @CsvSource({
            "2026-04-15, 2026-04, 2026-04-01, 2026-04-30",
            "2026-02-15, 2026-02, 2026-02-01, 2026-02-28", // 2026는 평년
            "2024-02-15, 2024-02, 2024-02-01, 2024-02-29", // 2024는 윤년
            "2026-12-31, 2026-12, 2026-12-01, 2026-12-31"
    })
    void monthBoundaries(LocalDate date, String expectedMonth,
                          LocalDate expectedStart, LocalDate expectedEnd) {
        assertThat(IsoPeriodMath.periodMonthOf(date)).isEqualTo(expectedMonth);
        assertThat(IsoPeriodMath.startOfMonth(date)).isEqualTo(expectedStart);
        assertThat(IsoPeriodMath.endOfMonth(date)).isEqualTo(expectedEnd);
    }

    @DisplayName("완결 판정: runDate > periodEnd 이면 finalized")
    @ParameterizedTest
    @CsvSource({
            // 현재 진행 중인 주 (runDate == periodEnd day)
            "2026-04-19, 2026-04-19, false",
            // 주가 끝난 다음 날 배치
            "2026-04-20, 2026-04-19, true",
            // 훨씬 이후 배치
            "2026-05-01, 2026-04-19, true",
            // 배치가 주 중간에 — 아직 안 끝남
            "2026-04-15, 2026-04-19, false"
    })
    void isPeriodFinalized(LocalDate runDate, LocalDate periodEnd, boolean expected) {
        assertThat(IsoPeriodMath.isPeriodFinalized(runDate, periodEnd)).isEqualTo(expected);
    }

    @DisplayName("targetDate 파싱 — yyyyMMdd")
    @ParameterizedTest
    @CsvSource({
            "20260415, 2026-04-15",
            "20260101, 2026-01-01",
            "20261231, 2026-12-31"
    })
    void parseTargetDate(String input, LocalDate expected) {
        assertThat(IsoPeriodMath.parseTargetDate(input)).isEqualTo(expected);
    }

    @DisplayName("targetDate null/빈문자 → 예외")
    @ParameterizedTest
    @CsvSource(value = {"null", "''", "' '"}, nullValues = "null")
    void parseTargetDate_invalid(String input) {
        assertThatThrownBy(() -> IsoPeriodMath.parseTargetDate(input))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

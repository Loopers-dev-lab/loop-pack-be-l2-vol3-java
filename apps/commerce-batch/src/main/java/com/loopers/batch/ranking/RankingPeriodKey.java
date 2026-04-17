package com.loopers.batch.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 주간/월간 랭킹 집계에 사용하는 기간 키를 생성한다.
 * <p>
 * 이전 라운드와 동일하게 {@code yyyyMMdd} 형식을 사용하며,
 * 주간/월간은 각각 대표 일자(앵커 날짜)를 {@code yyyyMMdd}로 표현한다.
 */
public final class RankingPeriodKey {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private RankingPeriodKey() {
    }

    /**
     * 주간 랭킹 기간 키를 생성한다.
     * <p>
     * ISO-8601 week 규칙으로 같은 주에 속하는 날짜들은 모두 동일한 대표 일자(예: 해당 주의 월요일)로 매핑되고,
     * 반환 형식은 {@code yyyyMMdd}이다.
     *
     * @param date 기간에 속한 임의의 날짜 (Asia/Seoul 달력 기준으로 계산된 일자)
     * @return 예: {@code 20260406} (2026년 4월 6일이 속한 주의 대표 일자)
     * @throws IllegalArgumentException date가 null인 경우
     */
    public static String weekly(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        // ISO 기준으로 같은 주에 속하는 날짜들이 모두 같은 월요일 앵커로 귀속되도록 한다.
        LocalDate anchor = date.with(java.time.DayOfWeek.MONDAY);
        return DATE.format(anchor);
    }

    /**
     * 월간 랭킹 기간 키를 생성한다.
     * <p>
     * 반환 형식은 {@code yyyyMMdd}이며, 해당 월의 첫째 날을 대표 일자로 사용한다.
     *
     * @param date 기간에 속한 임의의 날짜 (Asia/Seoul 달력 기준으로 계산된 일자)
     * @return 예: {@code 20260401}
     * @throws IllegalArgumentException date가 null인 경우
     */
    public static String monthly(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        LocalDate firstDayOfMonth = date.withDayOfMonth(1);
        return DATE.format(firstDayOfMonth);
    }
}


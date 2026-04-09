package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 랭킹 Redis ZSET 키 포맷터 (commerce-api 읽기 전용).
 *
 * <p>commerce-streamer 의 동일 이름 클래스와 포맷을 **정확히 일치시켜야** 한다 —
 * 양쪽 테스트가 같은 리터럴("ranking:all:yyyyMMdd") 을 고정해 회귀를 방지한다.
 */
public final class RankingKey {

    public static final String DAILY_PREFIX = "ranking:all:";
    public static final DateTimeFormatter DAILY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private RankingKey() {
    }

    public static String daily(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        return DAILY_PREFIX + date.format(DAILY_FORMATTER);
    }
}

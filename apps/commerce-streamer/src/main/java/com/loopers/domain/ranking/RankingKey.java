package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 랭킹 Redis ZSET 키 포맷터.
 *
 * 일간 랭킹은 `ranking:all:{yyyyMMdd}` 패턴을 사용한다.
 * commerce-api 에도 동일 포맷을 가진 클래스가 있으며, 두 앱 양쪽에서
 * 같은 리터럴을 고정해 회귀를 방지한다.
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

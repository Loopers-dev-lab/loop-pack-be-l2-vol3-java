package com.loopers.support.redis;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class RankingKeyConstants {

    public static final String DAY_KEY_PREFIX = "ranking:day:";
    public static final String HOUR_KEY_PREFIX = "ranking:hour:";

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("uuuuMMdd");
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("uuuuMMddHH");

    private RankingKeyConstants() {
    }

    public static String dayKey(LocalDate date) {
        return DAY_KEY_PREFIX + dayBucket(date);
    }

    public static String dayKey(String dateStr) {
        return DAY_KEY_PREFIX + dateStr;
    }

    public static String hourKey(LocalDateTime dateTime) {
        return HOUR_KEY_PREFIX + hourBucket(dateTime);
    }

    /**
     * day bucket key (Redis 키 prefix 없는 순수 날짜 문자열). DB ledger의 bucket_key 컬럼에도 사용.
     */
    public static String dayBucket(LocalDate date) {
        return date.format(DAY_FORMAT);
    }

    /**
     * hour bucket key (Redis 키 prefix 없는 순수 날짜+시간 문자열).
     */
    public static String hourBucket(LocalDateTime dateTime) {
        return dateTime.format(HOUR_FORMAT);
    }
}

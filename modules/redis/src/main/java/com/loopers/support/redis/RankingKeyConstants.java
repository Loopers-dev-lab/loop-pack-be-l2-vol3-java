package com.loopers.support.redis;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class RankingKeyConstants {

    public static final String DAY_KEY_PREFIX = "ranking:day:";
    public static final String HOUR_KEY_PREFIX = "ranking:hour:";

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private RankingKeyConstants() {
    }

    public static String dayKey(LocalDate date) {
        return DAY_KEY_PREFIX + date.format(DAY_FORMAT);
    }

    public static String dayKey(String dateStr) {
        return DAY_KEY_PREFIX + dateStr;
    }

    public static String hourKey(LocalDateTime dateTime) {
        return HOUR_KEY_PREFIX + dateTime.format(HOUR_FORMAT);
    }
}

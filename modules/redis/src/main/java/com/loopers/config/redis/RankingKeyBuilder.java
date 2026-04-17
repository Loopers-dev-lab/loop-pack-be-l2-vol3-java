package com.loopers.config.redis;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class RankingKeyBuilder {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static final long TTL_DAYS = 2;

    private RankingKeyBuilder() {}

    public static String buildKey(LocalDate date) {
        return KEY_PREFIX + date.format(DATE_FORMAT);
    }
}

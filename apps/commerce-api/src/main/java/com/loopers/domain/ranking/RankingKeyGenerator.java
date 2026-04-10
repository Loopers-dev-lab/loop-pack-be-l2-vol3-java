package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class RankingKeyGenerator {
    private static final String KEY_PREFIX = "ranking:all:";

    private RankingKeyGenerator() {}

    public static String todayKey() {
        return keyOf(LocalDate.now());
    }

    public static String keyOf(LocalDate date) {
        return KEY_PREFIX + date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}

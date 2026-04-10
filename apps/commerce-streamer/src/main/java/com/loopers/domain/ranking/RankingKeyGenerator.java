package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class RankingKeyGenerator {

    private static final String PREFIX = "ranking:all:";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static String dailyKey(LocalDate date) {
        return PREFIX + date.format(FORMATTER);
    }

    public static String todayKey() {
        return dailyKey(LocalDate.now());
    }
}

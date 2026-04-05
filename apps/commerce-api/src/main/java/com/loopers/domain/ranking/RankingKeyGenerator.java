package com.loopers.domain.ranking;

import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class RankingKeyGenerator {

    private static final String KEY_PREFIX = "rank:all:";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static String dailyKey(LocalDate date) {
        return KEY_PREFIX + date.format(FORMATTER);
    }
}

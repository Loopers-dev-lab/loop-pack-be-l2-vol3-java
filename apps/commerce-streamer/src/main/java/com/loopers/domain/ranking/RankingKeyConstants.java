package com.loopers.domain.ranking;

import java.time.format.DateTimeFormatter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RankingKeyConstants {

    public static final String KEY_PREFIX = "ranking:v1:all:";
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
}

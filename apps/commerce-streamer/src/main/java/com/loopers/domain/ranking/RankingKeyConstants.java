package com.loopers.domain.ranking;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RankingKeyConstants {

    public static final String KEY_PREFIX = "ranking:v1:all:";
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int TTL_DAYS = 2;

    /**
     * 키 날짜의 자정을 기준점(anchor)으로 삼아 TTL을 동적 계산한다.
     *
     * <p>고정 TTL을 사용하면 carry-over 실행 시각(23:50)이 기준점이 되어
     * 만료 시각이 의도보다 10분 앞당겨진다. 키 날짜 + 2일 자정을
     * 만료 시각으로 고정하면 이 문제를 방지할 수 있다.</p>
     *
     * @param key ZSET 키 (예: {@code ranking:v1:all:20260409})
     * @return 현재 시각부터 만료 시각까지의 초 단위 TTL, 최소 0
     */
    public static long calculateTtlSeconds(String key) {
        LocalDate keyDate = LocalDate.parse(key.substring(KEY_PREFIX.length()), DATE_FORMAT);
        LocalDateTime expireAt = keyDate.plusDays(TTL_DAYS).atStartOfDay();
        long ttl = Duration.between(LocalDateTime.now(), expireAt).getSeconds();
        return Math.max(ttl, 0);
    }
}

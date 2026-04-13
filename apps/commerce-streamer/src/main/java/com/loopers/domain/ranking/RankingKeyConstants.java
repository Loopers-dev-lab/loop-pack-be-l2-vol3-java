package com.loopers.domain.ranking;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 랭킹 Redis Sorted Set 키 상수 및 유틸리티.
 *
 * <p>일간 키 형식: {@code ranking:v1:daily:{yyyyMMdd}}, TTL 2일</p>
 * <p>시간 키 형식: {@code ranking:v1:hourly:{yyyyMMddHH}}, TTL 2시간</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RankingKeyConstants {

    public static final String DAILY_KEY_PREFIX = "ranking:v1:daily:";
    public static final String HOURLY_KEY_PREFIX = "ranking:v1:hourly:";
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    public static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private static final int DAILY_TTL_DAYS = 2;
    private static final int HOURLY_TTL_HOURS = 2;

    /**
     * 키 prefix에 따라 TTL을 동적 계산한다.
     *
     * <ul>
     *   <li>daily 키: 키 날짜 + 2일 자정까지</li>
     *   <li>hourly 키: 키 시간 + 2시간까지</li>
     * </ul>
     *
     * @param key ZSET 키
     * @return 현재 시각부터 만료 시각까지의 초 단위 TTL, 최소 0
     */
    public static long calculateTtlSeconds(String key) {
        if (key.startsWith(HOURLY_KEY_PREFIX)) {
            return calculateHourlyTtl(key);
        }
        return calculateDailyTtl(key);
    }

    /**
     * 현재 시간 기준 hourly 키를 반환한다.
     *
     * @return 현재 시간의 ZSET 키 (예: {@code ranking:v1:hourly:2026040913})
     */
    public static String currentHourKey() {
        return hourlyKey(LocalDateTime.now());
    }

    /**
     * 주어진 시각 기준 hourly 키를 반환한다.
     *
     * @param dateTime 기준 시각
     * @return 해당 시간의 ZSET 키 (예: {@code ranking:v1:hourly:2026040913})
     */
    public static String hourlyKey(LocalDateTime dateTime) {
        return HOURLY_KEY_PREFIX + dateTime.truncatedTo(ChronoUnit.HOURS).format(HOUR_FORMAT);
    }

    /**
     * 다음 시간 기준 hourly 키를 반환한다.
     *
     * @return 다음 시간의 ZSET 키 (예: {@code ranking:v1:hourly:2026040914})
     */
    public static String nextHourKey() {
        return HOURLY_KEY_PREFIX + LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(1).format(HOUR_FORMAT);
    }

    private static long calculateDailyTtl(String key) {
        LocalDate keyDate = LocalDate.parse(key.substring(DAILY_KEY_PREFIX.length()), DATE_FORMAT);
        LocalDateTime expireAt = keyDate.plusDays(DAILY_TTL_DAYS).atStartOfDay();
        long ttl = Duration.between(LocalDateTime.now(), expireAt).getSeconds();
        return Math.max(ttl, 0);
    }

    private static long calculateHourlyTtl(String key) {
        String suffix = key.substring(HOURLY_KEY_PREFIX.length());
        LocalDate date = LocalDate.parse(suffix.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
        int hour = Integer.parseInt(suffix.substring(8));
        LocalDateTime keyHour = date.atTime(hour, 0);
        LocalDateTime expireAt = keyHour.plusHours(HOURLY_TTL_HOURS);
        long ttl = Duration.between(LocalDateTime.now(), expireAt).getSeconds();
        return Math.max(ttl, 0);
    }
}

package com.loopers.config.redis;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 랭킹 Redis 키 상수.
 *
 * commerce-api와 commerce-streamer 모두 redis 모듈을 의존하므로
 * 이곳에 정의하여 키 불일치를 방지한다.
 */
public final class RankingKeys {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 일간 랭킹 Sorted Set 키 접두사: ranking:all:{yyyyMMdd} */
    public static final String DAILY_PREFIX = "ranking:all:";

    /** weight 캐시 키 접두사: ranking:weight:{eventType} */
    public static final String WEIGHT_PREFIX = "ranking:weight:";

    /** 시간별 랭킹 Sorted Set 키 접두사: ranking:hour:{yyyyMMddHH} */
    public static final String HOURLY_PREFIX = "ranking:hour:";

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static String dailyKey(LocalDate date) {
        return DAILY_PREFIX + date.format(DATE_FORMAT);
    }

    public static String hourlyKey(LocalDate date, int hour) {
        return HOURLY_PREFIX + date.format(DATE_FORMAT) + String.format("%02d", hour);
    }

    public static String weightKey(String eventType) {
        return WEIGHT_PREFIX + eventType;
    }

    /**
     * 해당 날짜 키의 TTL을 반환한다.
     *
     * keyDate+2일 00:00 (Asia/Seoul 기준) 에 만료되도록 계산한다.
     * 예) keyDate=오늘 → 내일 자정 이후까지만 조회 가능 (어제 랭킹 조회 지원)
     */
    public static Duration dailyTtl(LocalDate keyDate) {
        ZonedDateTime expiry = keyDate.plusDays(2).atStartOfDay(SEOUL);
        ZonedDateTime now = ZonedDateTime.now(SEOUL);
        Duration ttl = Duration.between(now, expiry);
        return ttl.isNegative() ? Duration.ofMinutes(1) : ttl;
    }

    private RankingKeys() {
    }
}

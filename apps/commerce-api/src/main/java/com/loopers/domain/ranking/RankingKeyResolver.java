package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.experimental.UtilityClass;

/**
 * 랭킹 Redis Sorted Set 키를 생성하는 유틸리티.
 *
 * <p>일간 키 형식: {@code ranking:v1:daily:{yyyyMMdd}}</p>
 * <p>시간 키 형식: {@code ranking:v1:hourly:{yyyyMMddHH}}</p>
 */
@UtilityClass
public class RankingKeyResolver {

    private static final String DAILY_PREFIX = "ranking:v1:daily:";
    private static final String HOURLY_PREFIX = "ranking:v1:hourly:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    /**
     * 주어진 날짜 문자열로 일간 랭킹 키를 생성한다. null이면 오늘 날짜를 사용한다.
     *
     * @param date yyyyMMdd 형식의 날짜 문자열 (nullable)
     * @return 일간 랭킹 Redis 키
     * @throws CoreException 날짜 형식이 올바르지 않을 때
     */
    public String resolveDaily(String date) {
        if (Objects.isNull(date)) {
            return DAILY_PREFIX + LocalDate.now().format(DATE_FORMAT);
        }
        validateDateFormat(date);
        return DAILY_PREFIX + date;
    }

    /**
     * 주어진 날짜시간 문자열로 시간 단위 랭킹 키를 생성한다. null이면 현재 시간을 사용한다.
     *
     * @param datetime yyyyMMddHH 형식의 날짜시간 문자열 (nullable)
     * @return 시간 단위 랭킹 Redis 키
     * @throws CoreException 날짜시간 형식이 올바르지 않을 때
     */
    public String resolveHourly(String datetime) {
        if (Objects.isNull(datetime)) {
            return HOURLY_PREFIX + LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).format(HOUR_FORMAT);
        }
        validateDatetimeFormat(datetime);
        return HOURLY_PREFIX + datetime;
    }

    private void validateDateFormat(String date) {
        try {
            LocalDate.parse(date, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.INVALID_RANKING_DATE_FORMAT);
        }
    }

    private void validateDatetimeFormat(String datetime) {
        try {
            LocalDateTime.parse(datetime, HOUR_FORMAT);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.INVALID_RANKING_DATETIME_FORMAT);
        }
    }
}

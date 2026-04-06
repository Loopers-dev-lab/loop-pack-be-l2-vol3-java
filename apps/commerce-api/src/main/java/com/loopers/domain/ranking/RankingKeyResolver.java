package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.experimental.UtilityClass;

/**
 * 랭킹 Redis Sorted Set 키를 생성하는 유틸리티.
 *
 * <p>키 형식: {@code ranking:v1:all:{yyyyMMdd}}</p>
 */
@UtilityClass
public class RankingKeyResolver {

    private static final String PREFIX = "ranking:v1:all:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 주어진 날짜 문자열로 랭킹 키를 생성한다. null이면 오늘 날짜를 사용한다.
     *
     * @param date yyyyMMdd 형식의 날짜 문자열 (nullable)
     * @return 랭킹 Redis 키
     * @throws CoreException 날짜 형식이 올바르지 않을 때
     */
    public String resolve(String date) {
        if (Objects.isNull(date)) {
            return resolveToday();
        }
        validateDateFormat(date);
        return PREFIX + date;
    }

    /**
     * 오늘 날짜 기준으로 랭킹 키를 생성한다.
     *
     * @return 오늘 날짜의 랭킹 Redis 키
     */
    public String resolveToday() {
        return PREFIX + LocalDate.now().format(DATE_FORMAT);
    }

    private void validateDateFormat(String date) {
        try {
            LocalDate.parse(date, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.INVALID_RANKING_DATE_FORMAT);
        }
    }
}

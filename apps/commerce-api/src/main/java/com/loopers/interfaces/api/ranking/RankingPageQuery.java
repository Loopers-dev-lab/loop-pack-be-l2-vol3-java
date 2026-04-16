package com.loopers.interfaces.api.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 랭킹 조회 요청 파라미터 파싱 결과.
 *
 * 세 랭킹 Controller(일간/주간/월간)가 공유하는 파라미터 파싱·보정 로직을 한 곳에 모은다.
 */
record RankingPageQuery(LocalDate date, int page, int size) {

    static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    static final int DEFAULT_PAGE = 1;
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    /**
     * 요청 파라미터를 파싱하고 보정하여 RankingPageQuery 를 생성한다.
     *
     * - date : null 또는 공백이면 null 반환 (Facade 기본값 처리에 위임)
     * - page : 1 미만이면 1로 보정
     * - size : 0 이하이면 DEFAULT_SIZE, MAX_SIZE 초과이면 MAX_SIZE 로 보정
     *
     * @throws CoreException BAD_REQUEST — dateStr 형식이 yyyyMMdd 가 아닌 경우
     */
    static RankingPageQuery of(String dateStr, int page, int size) {
        LocalDate date = parseDate(dateStr);
        int safePage = Math.max(page, DEFAULT_PAGE);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new RankingPageQuery(date, safePage, safeSize);
    }

    /** effectiveDate 를 yyyyMMdd 형식 문자열로 변환한다. */
    String formattedDate(LocalDate effectiveDate) {
        return effectiveDate.format(YYYYMMDD);
    }

    private static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr, YYYYMMDD);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date 는 yyyyMMdd 형식이어야 합니다: " + dateStr, e);
        }
    }
}

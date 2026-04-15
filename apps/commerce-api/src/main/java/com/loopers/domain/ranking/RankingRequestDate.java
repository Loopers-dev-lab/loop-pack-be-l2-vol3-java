package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 랭킹 API·상품 상세 랭킹 등에서 공통으로 쓰는 일자 쿼리 파라미터({@code yyyyMMdd}) 해석.
 * <p>
 * 생략 시 오늘 날짜(Asia/Seoul)를 사용한다.
 */
public final class RankingRequestDate {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter BASIC_ISO = DateTimeFormatter.BASIC_ISO_DATE;

    private RankingRequestDate() {
    }

    /**
     * {@code yyyyMMdd} 또는 생략(오늘, Asia/Seoul)을 {@link LocalDate}로 변환한다.
     *
     * @param raw 쿼리 {@code date} 값, null·빈 문자열이면 오늘
     * @return 랭킹 일자
     * @throws CoreException 형식이 잘못된 경우 {@link ErrorType#BAD_REQUEST}
     */
    public static LocalDate resolveOptionalYyyyMmDd(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.now(SEOUL);
        }
        try {
            return LocalDate.parse(raw.strip(), BASIC_ISO);
        } catch (DateTimeException ex) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date는 yyyyMMdd 형식이어야 합니다.");
        }
    }
}

package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingPage;
import com.loopers.domain.ranking.RankingQueryService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 랭킹 유스케이스 조율: 요청 문자열을 도메인 날짜로 변환하고 {@link RankingQueryService}에 위임한다.
 * HTTP DTO와 도메인 결과를 매핑한다.
 * <p>
 * {@code date} 미지정 시 오늘(Asia/Seoul)을 쓰며, 이에 대응하는 Redis 키 {@code ranking:all:{yyyyMMdd}}를 조회한다(동적 날짜 키).
 */
@Service
public class RankingFacade {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RankingQueryService rankingQueryService;

    public RankingFacade(RankingQueryService rankingQueryService) {
        this.rankingQueryService = rankingQueryService;
    }

    /**
     * 일간 인기 상품 랭킹 조회
     *
     * @param dateYyyyMmDdOptional 일자 yyyyMMdd (선택, 기본: 오늘(Asia/Seoul))
     * @param page 페이지 (1부터)
     * @param size 페이지 크기
     * @return 일간 인기 상품 랭킹 결과
     */
    @Transactional(readOnly = true)
    public RankingListInfo getRankings(String dateYyyyMmDdOptional, int page, int size) {
        LocalDate date = resolveRankingDate(dateYyyyMmDdOptional);
        RankingPage pageResult = rankingQueryService.loadPage(date, page, size);
        return RankingListInfo.from(pageResult);
    }

    /**
     * 일자를 해석한다.
     *
     * @param raw 일자 yyyyMMdd 문자열
     * @return 일자 (기본: 오늘(Asia/Seoul))
     */
    private LocalDate resolveRankingDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.now(SEOUL);
        }
        try {
            return LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeException ex) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date는 yyyyMMdd 형식이어야 합니다.");
        }
    }
}

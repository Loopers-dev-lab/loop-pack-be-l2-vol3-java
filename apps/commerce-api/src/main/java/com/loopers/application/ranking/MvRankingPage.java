package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingPeriod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 기간별 랭킹 응답 DTO.
 *
 * <ul>
 *     <li>DAILY — {@code baseDate} = 조회 날짜, {@code yearMonth}/{@code windowDays}/{@code aggregatedAt} null</li>
 *     <li>WEEKLY — {@code baseDate} = 윈도우 끝, {@code windowDays} = 실제 데이터가 있는 일 수 (최대 7)</li>
 *     <li>MONTHLY — {@code yearMonth} = "yyyy-MM"</li>
 * </ul>
 */
public record MvRankingPage(
        RankingPeriod period,
        LocalDate baseDate,
        String yearMonth,
        Integer windowDays,
        LocalDateTime aggregatedAt,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<RankingInfo> items
) {

    public static MvRankingPage daily(LocalDate date, int page, int size,
                                      long totalElements, int totalPages,
                                      List<RankingInfo> items) {
        return new MvRankingPage(
                RankingPeriod.DAILY, date, null, null, null,
                page, size, totalElements, totalPages, items);
    }

    public static MvRankingPage weekly(LocalDate baseDate, int windowDays, LocalDateTime aggregatedAt,
                                       int page, int size, long totalElements, int totalPages,
                                       List<RankingInfo> items) {
        return new MvRankingPage(
                RankingPeriod.WEEKLY, baseDate, null, windowDays, aggregatedAt,
                page, size, totalElements, totalPages, items);
    }

    public static MvRankingPage monthly(String yearMonth, LocalDateTime aggregatedAt,
                                        int page, int size, long totalElements, int totalPages,
                                        List<RankingInfo> items) {
        return new MvRankingPage(
                RankingPeriod.MONTHLY, null, yearMonth, null, aggregatedAt,
                page, size, totalElements, totalPages, items);
    }
}

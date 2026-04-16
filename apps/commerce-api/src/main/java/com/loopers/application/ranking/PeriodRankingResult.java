package com.loopers.application.ranking;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 기간별 랭킹 조회 결과.
 * 일간/주간/월간 랭킹을 통합하여 표현합니다.
 */
public record PeriodRankingResult(
    int rank,
    Long productId,
    String productName,
    Long productPrice,
    String productImageUrl,
    BigDecimal score,
    Long viewCount,
    Long likeCount,
    Long orderCount,
    RankingPeriod period,
    LocalDate periodStart,
    LocalDate periodEnd
) {
    /**
     * 일간 랭킹용 팩토리 메서드 (Redis 기반).
     * viewCount, likeCount, orderCount는 일간 랭킹에서는 별도 집계하지 않으므로 null로 설정합니다.
     */
    public static PeriodRankingResult ofDaily(
        int rank,
        Long productId,
        String productName,
        Long productPrice,
        String productImageUrl,
        Double score,
        LocalDate date
    ) {
        return new PeriodRankingResult(
            rank,
            productId,
            productName,
            productPrice,
            productImageUrl,
            score != null ? BigDecimal.valueOf(score) : null,
            null,  // viewCount
            null,  // likeCount
            null,  // orderCount
            RankingPeriod.DAILY,
            date,
            date
        );
    }

    /**
     * 주간/월간 랭킹용 팩토리 메서드 (DB 배치 집계 기반).
     */
    public static PeriodRankingResult ofPeriod(
        int rank,
        Long productId,
        String productName,
        Long productPrice,
        String productImageUrl,
        BigDecimal score,
        Long viewCount,
        Long likeCount,
        Long orderCount,
        RankingPeriod period,
        LocalDate periodStart,
        LocalDate periodEnd
    ) {
        return new PeriodRankingResult(
            rank,
            productId,
            productName,
            productPrice,
            productImageUrl,
            score,
            viewCount,
            likeCount,
            orderCount,
            period,
            periodStart,
            periodEnd
        );
    }
}
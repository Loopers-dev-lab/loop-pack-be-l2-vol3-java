package com.loopers.batch.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 월간 상품 랭킹 도메인 모델.
 * 매월 배치 Job에 의해 생성되며, TOP 100 상품의 월간 집계 데이터를 보관합니다.
 */
public class ProductRankMonthly {

    private final Long id;
    private final Long productId;
    private final int rankNumber;
    private final BigDecimal totalScore;
    private final long totalViewCount;
    private final long totalLikeCount;
    private final long totalOrderCount;
    private final LocalDate periodStartDate;
    private final LocalDate periodEndDate;
    private final LocalDateTime createdAt;

    private ProductRankMonthly(
        Long id,
        Long productId,
        int rankNumber,
        BigDecimal totalScore,
        long totalViewCount,
        long totalLikeCount,
        long totalOrderCount,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        LocalDateTime createdAt
    ) {
        this.id = id;
        this.productId = productId;
        this.rankNumber = rankNumber;
        this.totalScore = totalScore;
        this.totalViewCount = totalViewCount;
        this.totalLikeCount = totalLikeCount;
        this.totalOrderCount = totalOrderCount;
        this.periodStartDate = periodStartDate;
        this.periodEndDate = periodEndDate;
        this.createdAt = createdAt;
    }

    /**
     * 새로운 월간 랭킹 엔트리를 생성합니다.
     */
    public static ProductRankMonthly create(
        Long productId,
        int rankNumber,
        BigDecimal totalScore,
        long totalViewCount,
        long totalLikeCount,
        long totalOrderCount,
        LocalDate periodStartDate,
        LocalDate periodEndDate
    ) {
        return new ProductRankMonthly(
            null,
            productId,
            rankNumber,
            totalScore,
            totalViewCount,
            totalLikeCount,
            totalOrderCount,
            periodStartDate,
            periodEndDate,
            LocalDateTime.now()
        );
    }

    /**
     * 영속성 계층에서 도메인 객체를 복원합니다.
     */
    public static ProductRankMonthly reconstitute(
        Long id,
        Long productId,
        int rankNumber,
        BigDecimal totalScore,
        long totalViewCount,
        long totalLikeCount,
        long totalOrderCount,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        LocalDateTime createdAt
    ) {
        return new ProductRankMonthly(
            id, productId, rankNumber, totalScore,
            totalViewCount, totalLikeCount, totalOrderCount,
            periodStartDate, periodEndDate, createdAt
        );
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public int getRankNumber() {
        return rankNumber;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public long getTotalViewCount() {
        return totalViewCount;
    }

    public long getTotalLikeCount() {
        return totalLikeCount;
    }

    public long getTotalOrderCount() {
        return totalOrderCount;
    }

    public LocalDate getPeriodStartDate() {
        return periodStartDate;
    }

    public LocalDate getPeriodEndDate() {
        return periodEndDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
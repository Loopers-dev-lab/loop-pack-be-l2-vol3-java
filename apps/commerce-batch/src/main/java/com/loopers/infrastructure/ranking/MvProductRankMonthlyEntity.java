package com.loopers.infrastructure.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * 월간 상품 랭킹 Materialized View
 *
 * 캘린더 월(1일~말일) 단위 TOP 100 상품 집계 결과를 저장한다.
 * Source of truth는 ranking_event 테이블. 주간과 독립 파생 (주간 합산 금지).
 *
 * 계약은 주간과 동일:
 * - TOP 100만 저장
 * - tie-break: score DESC, product_id ASC
 * - is_finalized 상태 기계
 * - DELETE + INSERT 원자 swap
 *
 * 컬럼명 주의: MySQL 예약어 YEAR_MONTH 회피를 위해 period_month로 명명.
 */
@Entity
@Table(name = "mv_product_rank_monthly",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mv_monthly_month_rank",
                        columnNames = {"period_month", "rank_no"}),
                @UniqueConstraint(name = "uk_mv_monthly_month_product",
                        columnNames = {"period_month", "product_id"})
        },
        indexes = {
                @Index(name = "idx_mv_monthly_month", columnList = "period_month")
        })
public class MvProductRankMonthlyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_month", nullable = false, length = 7)
    private String periodMonth;

    @Column(name = "rank_no", nullable = false)
    private Integer rankNo;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "is_finalized", nullable = false)
    private Boolean isFinalized;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected MvProductRankMonthlyEntity() {}

    public static MvProductRankMonthlyEntity create(
            String periodMonth, int rankNo, long productId, double score,
            LocalDate periodStart, LocalDate periodEnd, boolean isFinalized) {
        MvProductRankMonthlyEntity entity = new MvProductRankMonthlyEntity();
        entity.periodMonth = periodMonth;
        entity.rankNo = rankNo;
        entity.productId = productId;
        entity.score = score;
        entity.periodStart = periodStart;
        entity.periodEnd = periodEnd;
        entity.isFinalized = isFinalized;
        entity.updatedAt = ZonedDateTime.now();
        return entity;
    }

    public Long getId() { return id; }
    public String getPeriodMonth() { return periodMonth; }
    public Integer getRankNo() { return rankNo; }
    public Long getProductId() { return productId; }
    public Double getScore() { return score; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public Boolean getIsFinalized() { return isFinalized; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
}

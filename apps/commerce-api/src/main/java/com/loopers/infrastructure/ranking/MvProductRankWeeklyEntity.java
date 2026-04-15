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
 * 주간 상품 랭킹 Materialized View (commerce-api 읽기용 매핑)
 *
 * commerce-batch의 동명 entity와 동일 테이블을 매핑.
 * ProductMetricsEntity 관례를 따라 각 모듈에서 독립 정의.
 *
 * 읽기 전용 — commerce-api는 조회만, 쓰기는 commerce-batch 담당.
 */
@Entity
@Table(name = "mv_product_rank_weekly",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mv_weekly_week_rank",
                        columnNames = {"year_week", "rank_no"}),
                @UniqueConstraint(name = "uk_mv_weekly_week_product",
                        columnNames = {"year_week", "product_id"})
        },
        indexes = {
                @Index(name = "idx_mv_weekly_week", columnList = "year_week")
        })
public class MvProductRankWeeklyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year_week", nullable = false, length = 10)
    private String yearWeek;

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

    protected MvProductRankWeeklyEntity() {}

    public Long getId() { return id; }
    public String getYearWeek() { return yearWeek; }
    public Integer getRankNo() { return rankNo; }
    public Long getProductId() { return productId; }
    public Double getScore() { return score; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public Boolean getIsFinalized() { return isFinalized; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
}

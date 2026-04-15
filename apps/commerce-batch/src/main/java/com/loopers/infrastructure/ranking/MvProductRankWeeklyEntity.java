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
 * 주간 상품 랭킹 Materialized View
 *
 * ISO 8601 캘린더 주 단위(월~일) TOP 100 상품 집계 결과를 저장한다.
 * Source of truth는 ranking_event 테이블이며, 이 테이블은 파생 산출물.
 *
 * 계약:
 * - TOP 100만 저장 (rank_no 1..100)
 * - 동률 tie-break: score DESC, product_id ASC
 * - is_finalized=false: 다음 배치가 덮어쓸 대상 (진행 중인 주)
 * - is_finalized=true:  주가 끝나 더 이상 변경 안 함
 * - 갱신 패턴: 단일 트랜잭션 내 DELETE WHERE year_week + INSERT 100 (원자 swap)
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

    public static MvProductRankWeeklyEntity create(
            String yearWeek, int rankNo, long productId, double score,
            LocalDate periodStart, LocalDate periodEnd, boolean isFinalized) {
        MvProductRankWeeklyEntity entity = new MvProductRankWeeklyEntity();
        entity.yearWeek = yearWeek;
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
    public String getYearWeek() { return yearWeek; }
    public Integer getRankNo() { return rankNo; }
    public Long getProductId() { return productId; }
    public Double getScore() { return score; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public Boolean getIsFinalized() { return isFinalized; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
}

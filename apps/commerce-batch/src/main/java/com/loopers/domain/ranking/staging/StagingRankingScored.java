package com.loopers.domain.ranking.staging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 2차 스테이징 — 1차 스테이징을 score 까지 계산해둔 전체 상품 격리 공간.
 * MV 에 TOP 100 만 진입시키기 위해, "전체 상품 score" 라는 중간 상태를 MV 가 아니라 여기에 둔다.
 * Step 5(Chunk) 가 채우고 Step 5b(Tasklet) 가 TOP 100 으로 MV 에 promote 한다.
 */
@Entity
@Table(
        name = "staging_ranking_scored",
        indexes = @Index(
                name = "idx_scored_sort",
                columnList = "period_type, period_key, weight_group, score"
        )
)
@IdClass(StagingRankingScoredId.class)
@Getter
public class StagingRankingScored {

    @Id
    @Column(name = "period_type", length = 16, nullable = false)
    private String periodType;

    @Id
    @Column(name = "period_key", length = 8, nullable = false)
    private String periodKey;

    @Id
    @Column(name = "weight_group", length = 32, nullable = false)
    private String weightGroup;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_amount", nullable = false)
    private long salesAmount;

    @Column(name = "score", nullable = false)
    private double score;

    protected StagingRankingScored() {
    }

    public StagingRankingScored(String periodType, String periodKey, String weightGroup, Long productId,
                                long viewCount, long likeCount, long salesAmount, double score) {
        this.periodType = periodType;
        this.periodKey = periodKey;
        this.weightGroup = weightGroup;
        this.productId = productId;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.salesAmount = salesAmount;
        this.score = score;
    }
}

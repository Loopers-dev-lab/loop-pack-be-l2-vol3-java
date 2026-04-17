package com.loopers.domain.ranking.staging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 1차 스테이징 — 각 메트릭(view/like/order) 을 product 단위로 합산한 raw sum 보관소.
 * Step 1~3 가 UPSERT 로 채우고 Step 5 가 읽어 간다.
 */
@Entity
@Table(name = "staging_ranking_aggregation")
@IdClass(StagingRankingAggregationId.class)
@Getter
public class StagingRankingAggregation {

    @Id
    @Column(name = "period_type", length = 16, nullable = false)
    private String periodType;

    @Id
    @Column(name = "period_key", length = 8, nullable = false)
    private String periodKey;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_amount", nullable = false)
    private long salesAmount;

    protected StagingRankingAggregation() {
    }

    public StagingRankingAggregation(String periodType, String periodKey, Long productId,
                                     long viewCount, long likeCount, long salesAmount) {
        this.periodType = periodType;
        this.periodKey = periodKey;
        this.productId = productId;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.salesAmount = salesAmount;
    }
}

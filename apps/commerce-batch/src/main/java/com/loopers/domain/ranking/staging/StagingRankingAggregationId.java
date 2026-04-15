package com.loopers.domain.ranking.staging;

import java.io.Serializable;
import java.util.Objects;

public class StagingRankingAggregationId implements Serializable {

    private String periodType;
    private String periodKey;
    private Long productId;

    public StagingRankingAggregationId() {
    }

    public StagingRankingAggregationId(String periodType, String periodKey, Long productId) {
        this.periodType = periodType;
        this.periodKey = periodKey;
        this.productId = productId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StagingRankingAggregationId that)) return false;
        return Objects.equals(periodType, that.periodType)
                && Objects.equals(periodKey, that.periodKey)
                && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(periodType, periodKey, productId);
    }
}

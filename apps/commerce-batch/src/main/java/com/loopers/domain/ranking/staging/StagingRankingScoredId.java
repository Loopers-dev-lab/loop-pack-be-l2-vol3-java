package com.loopers.domain.ranking.staging;

import java.io.Serializable;
import java.util.Objects;

public class StagingRankingScoredId implements Serializable {

    private String periodType;
    private String periodKey;
    private String weightGroup;
    private Long productId;

    public StagingRankingScoredId() {
    }

    public StagingRankingScoredId(String periodType, String periodKey, String weightGroup, Long productId) {
        this.periodType = periodType;
        this.periodKey = periodKey;
        this.weightGroup = weightGroup;
        this.productId = productId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StagingRankingScoredId that)) return false;
        return Objects.equals(periodType, that.periodType)
                && Objects.equals(periodKey, that.periodKey)
                && Objects.equals(weightGroup, that.weightGroup)
                && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(periodType, periodKey, weightGroup, productId);
    }
}

package com.loopers.domain.ranking.mv;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * mv_product_rank_last_7d / mv_product_rank_last_30d 공통 PK 클래스.
 * 자연 키: (anchor_date, weight_group, product_id)
 */
public class MvProductRankId implements Serializable {

    private LocalDate anchorDate;
    private String weightGroup;
    private Long productId;

    public MvProductRankId() {
    }

    public MvProductRankId(LocalDate anchorDate, String weightGroup, Long productId) {
        this.anchorDate = anchorDate;
        this.weightGroup = weightGroup;
        this.productId = productId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MvProductRankId that)) return false;
        return Objects.equals(anchorDate, that.anchorDate)
                && Objects.equals(weightGroup, that.weightGroup)
                && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(anchorDate, weightGroup, productId);
    }
}

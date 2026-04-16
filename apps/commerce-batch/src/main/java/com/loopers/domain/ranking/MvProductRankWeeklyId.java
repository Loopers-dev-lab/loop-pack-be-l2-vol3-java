package com.loopers.domain.ranking;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class MvProductRankWeeklyId implements Serializable {

    private Long productId;
    private LocalDate weekStartDate;

    protected MvProductRankWeeklyId() {}

    public MvProductRankWeeklyId(Long productId, LocalDate weekStartDate) {
        this.productId = productId;
        this.weekStartDate = weekStartDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MvProductRankWeeklyId that)) return false;
        return Objects.equals(productId, that.productId) && Objects.equals(weekStartDate, that.weekStartDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, weekStartDate);
    }
}

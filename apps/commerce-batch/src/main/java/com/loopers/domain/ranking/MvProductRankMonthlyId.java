package com.loopers.domain.ranking;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class MvProductRankMonthlyId implements Serializable {

    private Long productId;
    private LocalDate monthStartDate;

    protected MvProductRankMonthlyId() {}

    public MvProductRankMonthlyId(Long productId, LocalDate monthStartDate) {
        this.productId = productId;
        this.monthStartDate = monthStartDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MvProductRankMonthlyId that)) return false;
        return Objects.equals(productId, that.productId) && Objects.equals(monthStartDate, that.monthStartDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, monthStartDate);
    }
}

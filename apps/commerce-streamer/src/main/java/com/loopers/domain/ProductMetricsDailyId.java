package com.loopers.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class ProductMetricsDailyId implements Serializable {

    private Long productId;
    private LocalDate metricDate;

    protected ProductMetricsDailyId() {}

    public ProductMetricsDailyId(Long productId, LocalDate metricDate) {
        this.productId = productId;
        this.metricDate = metricDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductMetricsDailyId that)) return false;
        return Objects.equals(productId, that.productId) && Objects.equals(metricDate, that.metricDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, metricDate);
    }
}
